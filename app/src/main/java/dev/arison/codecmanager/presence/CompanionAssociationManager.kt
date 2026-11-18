package dev.arison.codecmanager.presence

import android.Manifest
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class CompanionAssociationManager(private val context: Context) {

    private val cdm: CompanionDeviceManager? =
        context.getSystemService(CompanionDeviceManager::class.java)

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(BluetoothManager::class.java)

    /**
     * The system no longer shows the association dialog by itself: it hands an [IntentSender] to
     * [CompanionDeviceManager.Callback.onAssociationPending] and the app has to launch it through
     * the Activity Result API.
     */
    var pendingIntentLauncher: ((android.content.IntentSender) -> Unit)? = null

    fun associations(): List<AssociationInfo> =
        runCatching { cdm?.myAssociations.orEmpty() }.getOrDefault(emptyList())

    fun associatedMacs(): Set<String> = associations()
        .mapNotNull { association -> associationMac(association) }
        .toSet()

    fun isAssociated(mac: String): Boolean =
        associatedMacs().any { it.equals(mac, ignoreCase = true) }

    fun associationMac(association: AssociationInfo): String? {
        if (Build.VERSION.SDK_INT >= 33) {
            return association.deviceMacAddress?.toString()?.uppercase()
        }
        @Suppress("DEPRECATION")
        return association.deviceMacAddress?.toString()?.uppercase()
    }

    fun pairedDevices(): List<BluetoothDevice> {
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return emptyList()
        }
        return runCatching {
            bluetoothManager?.adapter?.bondedDevices?.toList().orEmpty()
        }.getOrDefault(emptyList())
    }

    fun deviceMac(device: BluetoothDevice): String = device.address.uppercase()

    fun deviceName(device: BluetoothDevice): String =
        runCatching { device.alias?.takeIf { it.isNotBlank() } ?: device.name }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: device.address

    suspend fun associate(device: BluetoothDevice): Result<Unit> = withContext(Dispatchers.Main) {
        android.util.Log.i("ASSOC", "associate() start mac=${device.address} cdm=${cdm != null}")
        val manager = cdm ?: return@withContext Result.failure(
            IllegalStateException("CompanionDeviceManager is unavailable"),
        )
        suspendCancellableCoroutine { continuation ->
            val request = runCatching {
                AssociationRequest.Builder()
                    .addDeviceFilter(
                        BluetoothDeviceFilter.Builder()
                            .setAddress(device.address)
                            .build(),
                    )
                    .setSingleDevice(true)
                    .setDisplayName(deviceName(device))
                    .build()
            }.getOrElse { error ->
                android.util.Log.e("ASSOC", "request build failed", error)
                continuation.resume(Result.failure(error))
                return@suspendCancellableCoroutine
            }

            val callback = object : CompanionDeviceManager.Callback() {
                override fun onAssociationPending(intentSender: android.content.IntentSender) {
                    android.util.Log.i("ASSOC", "onAssociationPending")
                    val launcher = pendingIntentLauncher
                    if (launcher != null) {
                        launcher(intentSender)
                    } else {
                        android.util.Log.e("ASSOC", "no launcher bound for association prompt")
                    }
                }

                override fun onAssociationCreated(associationInfo: AssociationInfo) {
                    android.util.Log.i("ASSOC", "onAssociationCreated id=${associationInfo.id}")
                    if (continuation.isActive) continuation.resume(Result.success(Unit))
                }

                override fun onFailure(error: CharSequence?) {
                    android.util.Log.e("ASSOC", "onFailure: $error")
                    if (continuation.isActive) {
                        continuation.resume(
                            Result.failure(
                                IllegalStateException(error?.toString() ?: "Association failed"),
                            ),
                        )
                    }
                }
            }

            try {
                android.util.Log.i("ASSOC", "calling cdm.associate")
                manager.associate(request, ContextCompat.getMainExecutor(context), callback)
            } catch (error: Throwable) {
                android.util.Log.e("ASSOC", "cdm.associate threw", error)
                if (continuation.isActive) continuation.resume(Result.failure(error))
            }
        }
    }

    fun disassociate(mac: String) {
        runCatching { cdm?.disassociate(mac) }
    }
}
