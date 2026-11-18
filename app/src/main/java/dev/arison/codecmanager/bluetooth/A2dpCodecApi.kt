package dev.arison.codecmanager.bluetooth

import android.Manifest
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothCodecConfig
import android.bluetooth.BluetoothCodecStatus
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.withLock

/**
 * Thin reflection bridge over the hidden (but CompanionDeviceManager-gated) A2DP codec API.
 *
 * The framework checks `Utils.enforceCdmAssociationIfNotBluetoothPrivileged` for both
 * `getCodecStatus` and `setCodecConfigPreference`, which is exactly why this app asks the user to
 * associate headphones through CompanionDeviceManager during onboarding.
 */
class A2dpCodecApi(private val context: Context) {

    private val manager: BluetoothManager? =
        context.getSystemService(BluetoothManager::class.java)

    val adapter: BluetoothAdapter? get() = manager?.adapter

    private val getCodecStatusMethod: Method? by lazy {
        runCatching {
            BluetoothA2dp::class.java.getMethod("getCodecStatus", BluetoothDevice::class.java)
        }.getOrNull()
    }

    private val setCodecConfigPreferenceMethod: Method? by lazy {
        runCatching {
            BluetoothA2dp::class.java.getMethod(
                "setCodecConfigPreference",
                BluetoothDevice::class.java,
                BluetoothCodecConfig::class.java,
            )
        }.getOrNull()
    }

    private val setExtendedCodecTypeMethod: Method? by lazy {
        if (Build.VERSION.SDK_INT < 35) {
            null
        } else {
            runCatching {
                val type = Class.forName("android.bluetooth.BluetoothCodecType")
                BluetoothCodecConfig.Builder::class.java.getMethod("setExtendedCodecType", type)
            }.getOrNull()
        }
    }

    private val proxyMutex = kotlinx.coroutines.sync.Mutex()
    private var cachedProxy: BluetoothA2dp? = null

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    fun hasRequiredMethods(): Boolean =
        getCodecStatusMethod != null && setCodecConfigPreferenceMethod != null

    fun diagnostics(): String = buildString {
        append("getCodecStatus=")
        append(if (getCodecStatusMethod != null) "ok" else "missing")
        append(", setCodecConfigPreference=")
        append(if (setCodecConfigPreferenceMethod != null) "ok" else "missing")
    }

    suspend fun <T> withA2dp(block: suspend (BluetoothA2dp) -> T): T? {
        if (!hasPermission()) {
            throw CodecFailure(FailureReason.PERMISSION_DENIED, "BLUETOOTH_CONNECT is not granted")
        }
        val proxy = proxyMutex.withLock {
            cachedProxy ?: awaitProxy()?.also { cachedProxy = it }
        } ?: throw CodecFailure(
            FailureReason.NOT_CONNECTED,
            "A2DP profile proxy is unavailable",
        )
        return block(proxy)
    }

    private suspend fun awaitProxy(): BluetoothA2dp? = suspendCancellableCoroutine { cont ->
        val bluetoothAdapter = adapter
        if (bluetoothAdapter == null || !hasPermission() || !bluetoothAdapter.isEnabled) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                android.util.Log.i("CodecMgr", "A2DP proxy connected")
                if (cont.isActive) cont.resume(proxy as? BluetoothA2dp)
            }

            override fun onServiceDisconnected(profile: Int) {
                android.util.Log.i("CodecMgr", "A2DP proxy disconnected")
                cachedProxy = null
                if (cont.isActive) cont.resume(null)
            }
        }
        val requested = runCatching {
            bluetoothAdapter.getProfileProxy(context, listener, BluetoothProfile.A2DP)
        }.getOrDefault(false)
        if (!requested && cont.isActive) cont.resume(null)
    }

    suspend fun readStatus(device: BluetoothDevice): CodecStatusSnapshot {
        val method = getCodecStatusMethod ?: throw CodecFailure(
            FailureReason.HIDDEN_API_UNAVAILABLE,
            "BluetoothA2dp.getCodecStatus is not available on this firmware",
        )
        val raw = try {
            withA2dp { a2dp -> method.invoke(a2dp, device) }
        } catch (failure: CodecFailure) {
            throw failure
        } catch (throwable: Throwable) {
            throw throwable.toCodecFailure()
        }
        val status = raw as? BluetoothCodecStatus ?: throw CodecFailure(
            FailureReason.NOT_CONNECTED,
            "Codec status is null — the device is not ready",
        )
        return status.toSnapshot()
    }

    suspend fun setCodecConfig(device: BluetoothDevice, config: BluetoothCodecConfig) {
        val method = setCodecConfigPreferenceMethod ?: throw CodecFailure(
            FailureReason.HIDDEN_API_UNAVAILABLE,
            "BluetoothA2dp.setCodecConfigPreference is not available on this firmware",
        )
        try {
            withA2dp { a2dp -> method.invoke(a2dp, device, config) }
        } catch (failure: CodecFailure) {
            throw failure
        } catch (throwable: Throwable) {
            throw throwable.toCodecFailure()
        }
    }

    suspend fun isConnected(device: BluetoothDevice): Boolean {
        var state: Int? = null
        try {
            state = withA2dp { a2dp -> a2dp.getConnectionState(device) }
        } catch (failure: CodecFailure) {
            android.util.Log.w("CodecMgr", "isConnected failed: ${failure.message}")
        }
        val adapterState = runCatching {
            adapter?.getProfileConnectionState(BluetoothProfile.A2DP)
        }.getOrNull()
        android.util.Log.i(
            "CodecMgr",
            "isConnected ${device.address}: proxy=$state adapter=$adapterState",
        )
        return state == BluetoothProfile.STATE_CONNECTED
    }

    suspend fun connectedDevices(): List<BluetoothDevice> =
        try {
            withA2dp { a2dp -> a2dp.connectedDevices } ?: emptyList()
        } catch (_: CodecFailure) {
            emptyList()
        }

    fun buildConfig(
        current: CodecConfigSnapshot,
        resolved: ResolvedTarget,
    ): BluetoothCodecConfig {
        val builder = BluetoothCodecConfig.Builder()
            .setCodecType(resolved.codecType)
            .setCodecPriority(HIGHEST_PRIORITY)
            .setSampleRate(resolved.sampleRate)
            .setBitsPerSample(resolved.bitsPerSample)
            .setChannelMode(resolved.channelMode)
            .setCodecSpecific1(resolved.specific1)
            .setCodecSpecific2(current.specific2)
            .setCodecSpecific3(current.specific3)
            .setCodecSpecific4(current.specific4)

        val extended = current.extendedTypeRef
        val setter = setExtendedCodecTypeMethod
        if (extended != null && setter != null) {
            runCatching { setter.invoke(builder, extended) }
        }
        return builder.build()
    }

    private fun BluetoothCodecStatus.toSnapshot(): CodecStatusSnapshot {
        val currentSnapshot = codecConfig?.toSnapshot()
            ?: throw CodecFailure(FailureReason.NOT_CONNECTED, "Current codec config is null")
        val selectable = codecsSelectableCapabilities.map { it.toSnapshot() }
        val capabilities = codecsSelectableCapabilities.map { it.toCapabilities() }
        return CodecStatusSnapshot(
            current = currentSnapshot,
            selectable = selectable,
            capabilities = capabilities,
        )
    }

    private fun BluetoothCodecConfig.toSnapshot(): CodecConfigSnapshot {
        var extendedId: Long? = null
        var extendedRef: Any? = null
        if (Build.VERSION.SDK_INT >= 35) {
            runCatching {
                val ext = extendedCodecType
                if (ext != null) {
                    extendedRef = ext
                    extendedId = ext.codecId
                }
            }
        }
        return CodecConfigSnapshot(
            codecType = codecType,
            codecName = codecKindName(codecType),
            extendedCodecTypeId = extendedId,
            extendedCodecName = null,
            priority = codecPriority,
            sampleRate = sampleRate,
            bitsPerSample = bitsPerSample,
            channelMode = channelMode,
            specific1 = codecSpecific1,
            specific2 = codecSpecific2,
            specific3 = codecSpecific3,
            specific4 = codecSpecific4,
            extendedTypeRef = extendedRef,
        )
    }

    private fun BluetoothCodecConfig.toCapabilities(): CodecCapabilitiesSnapshot {
        var extendedId: Long? = null
        if (Build.VERSION.SDK_INT >= 35) {
            runCatching { extendedId = extendedCodecType?.codecId }
        }
        return CodecCapabilitiesSnapshot(
            codecType = codecType,
            extendedCodecTypeId = extendedId,
            sampleRatesMask = sampleRate,
            bitsPerSampleMask = bitsPerSample,
            channelModesMask = channelMode,
        )
    }

    private fun codecKindName(codecType: Int): String = CodecKind.of(codecType).displayName

    private fun Throwable.toCodecFailure(): CodecFailure {
        val root = (this as? InvocationTargetException)?.targetException ?: this
        val message = root.message.orEmpty()
        val lower = message.lowercase()
        val reason = when {
            root is NoSuchMethodException ||
                root is NoClassDefFoundError ||
                root is ClassNotFoundException -> FailureReason.HIDDEN_API_UNAVAILABLE
            root is SecurityException && (
                lower.contains("cdm") ||
                    lower.contains("association") ||
                    lower.contains("bluetooth_privileged")
                ) -> FailureReason.NOT_ASSOCIATED
            root is SecurityException -> FailureReason.PERMISSION_DENIED
            root is IllegalStateException && lower.contains("not connected") -> FailureReason.NOT_CONNECTED
            else -> FailureReason.UNKNOWN
        }
        return CodecFailure(reason, message.ifBlank { root::class.java.simpleName }, root)
    }

    companion object {
        const val HIGHEST_PRIORITY = 1_000_000
    }
}
