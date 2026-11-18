package dev.arison.codecmanager

import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import dev.arison.codecmanager.bluetooth.ApplyResult
import dev.arison.codecmanager.bluetooth.CodecEngine
import dev.arison.codecmanager.bluetooth.CodecResolver
import dev.arison.codecmanager.bluetooth.CodecTarget
import dev.arison.codecmanager.bluetooth.FailureReason
import dev.arison.codecmanager.bluetooth.LdacQuality
import dev.arison.codecmanager.bluetooth.PlaybackQualityMemory
import dev.arison.codecmanager.data.EventLog
import dev.arison.codecmanager.data.SettingsRepository
import dev.arison.codecmanager.presence.CompanionAssociationManager
import dev.arison.codecmanager.service.CodecNotifier
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ApplyEvent(
    val timestamp: Long,
    val mac: String,
    val deviceName: String,
    val quality: LdacQuality?,
    val result: ApplyResult,
    val source: String,
)

/**
 * Coordinates "device appeared" triggers from CompanionDeviceService, the A2DP broadcast receiver
 * and manual actions from the UI / notifications.
 */
class ConnectionCoordinator(
    context: Context,
    private val engine: CodecEngine,
    private val settings: SettingsRepository,
    private val associations: CompanionAssociationManager,
    private val log: EventLog,
    private val notifier: CodecNotifier,
) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val pendingRechecks = ConcurrentHashMap.newKeySet<String>()
    private val lastAppliedAt = ConcurrentHashMap<String, Long>()
    private val adapter =
        appContext.getSystemService(BluetoothManager::class.java)?.adapter

    private val _events = MutableStateFlow<ApplyEvent?>(null)
    val events: StateFlow<ApplyEvent?> = _events.asStateFlow()

    fun onDeviceAppeared(mac: String) {
        trigger(mac, source = "presence")
    }

    fun onA2dpConnected(mac: String) {
        trigger(mac, source = "a2dp")
    }

    fun onDeviceDisconnected(mac: String) {
        PlaybackQualityMemory.forget(mac)
        lastAppliedAt.remove(mac)
        pendingRechecks.remove(mac)
        log.d(TAG, "disconnected mac=$mac")
    }

    fun trigger(mac: String, source: String) {
        scope.launch {
            if (!inFlight.add(mac)) {
                pendingRechecks.add(mac)
                log.d(TAG, "trigger $source while busy mac=$mac, queued")
                return@launch
            }
            try {
                autoApply(mac, source)
                if (pendingRechecks.remove(mac)) {
                    delay(800)
                    autoApply(mac, "queued")
                }
            } finally {
                inFlight.remove(mac)
            }
        }
    }

    suspend fun applyNow(
        mac: String,
        quality: LdacQuality? = null,
        source: String = "manual",
    ): ApplyResult? {
        var waited = 0L
        while (!inFlight.add(mac)) {
            delay(200)
            waited += 200
            if (waited >= 20_000) break
        }
        return try {
            autoApply(mac, source, qualityOverride = quality, manual = true)
        } finally {
            inFlight.remove(mac)
        }
    }

    private suspend fun autoApply(
        mac: String,
        source: String,
        qualityOverride: LdacQuality? = null,
        manual: Boolean = false,
    ): ApplyResult? {
        if (!manual) {
            val last = lastAppliedAt[mac]
            if (last != null && System.currentTimeMillis() - last < 2_500) {
                log.d(TAG, "debounced trigger $source mac=$mac")
                return null
            }
        }
        try {
            val currentSettings = settings.settings.first()
            if (!manual && !currentSettings.autoSwitch) return null

            val profile = settings.profiles.first()
                .firstOrNull { it.mac.equals(mac, ignoreCase = true) }
            if (!manual && profile != null && (!profile.enabled || !profile.applyOnConnect)) {
                log.d(TAG, "profile disabled for mac=$mac")
                return null
            }

            val target = when {
                qualityOverride != null -> (profile?.codecTarget() ?: CodecTarget.Default)
                    .copy(ldacQuality = qualityOverride)
                profile != null -> profile.codecTarget()
                else -> CodecTarget.Default
            }

            val device = runCatching { adapter?.getRemoteDevice(mac) }.getOrNull()
            if (device == null) {
                log.w(TAG, "unknown device mac=$mac")
                return ApplyResult.Failure(FailureReason.NOT_CONNECTED, "Unknown device")
            }
            if (adapter?.isEnabled != true) {
                log.w(TAG, "adapter is off")
                return ApplyResult.Failure(FailureReason.ADAPTER_OFF, "Bluetooth is off")
            }

            val deviceName = associations.deviceName(device)
            log.i(TAG, "apply start device=$deviceName source=$source quality=${target.ldacQuality}")
            android.util.Log.i("CodecMgr", "apply start $deviceName source=$source quality=${target.ldacQuality}")

            if (!associations.isAssociated(mac)) {
                log.w(TAG, "device is not associated mac=$mac")
                val failure = ApplyResult.Failure(
                    FailureReason.NOT_ASSOCIATED,
                    "No CompanionDeviceManager association",
                )
                publish(mac, deviceName, target.ldacQuality, failure, source)
                if (manual && !isAppVisible()) {
                    notifier.notifyResult(mac, deviceName, target.ldacQuality, failure)
                }
                return failure
            }

            if (!waitForA2dp(device)) {
                log.w(TAG, "A2DP did not connect in time")
                return ApplyResult.Failure(FailureReason.NOT_CONNECTED, "A2DP is not connected")
            }
            // Let the stack finish codec negotiation before overriding it.
            delay(A2DP_SETTLE_MS)

            var result = engine.apply(device, target)
            if (result is ApplyResult.Failure && result.reason == FailureReason.CODEC_NOT_SUPPORTED) {
                // A non-LDAC device is not an error for the default LDAC-quality policy.
                log.i(TAG, "skipping $deviceName: ${result.message}")
                return result
            }
            if (result is ApplyResult.Rejected || (result is ApplyResult.Failure && result.reason == FailureReason.TIMEOUT)) {
                log.d(TAG, "retrying apply once")
                delay(1_500)
                result = engine.apply(device, target)
            }
            if (result is ApplyResult.Applied || result is ApplyResult.AlreadyApplied) {
                lastAppliedAt[mac] = System.currentTimeMillis()
            }

            publish(mac, deviceName, target.ldacQuality, result, source)
            if (currentSettings.notifications && !isAppVisible()) {
                notifier.notifyResult(mac, deviceName, target.ldacQuality, result)
            }
            if (result is ApplyResult.Applied || result is ApplyResult.AlreadyApplied) {
                scheduleRecheck(mac, deviceName, target, profile?.keepQuality == true)
            }
            return result
        } catch (throwable: Throwable) {
            log.e(TAG, "apply crashed: ${throwable.message}")
            return ApplyResult.Failure(
                FailureReason.UNKNOWN,
                throwable.message,
            )
        } finally {
            // inFlight is managed by trigger(); manual callers release it themselves.
        }
    }

    private fun publish(
        mac: String,
        deviceName: String,
        quality: LdacQuality?,
        result: ApplyResult,
        source: String,
    ) {
        val message = when (result) {
            is ApplyResult.Applied -> "applied${if (result.unverifiedQuality) " (unverified)" else ""} attempts=${result.attempts}"
            is ApplyResult.AlreadyApplied -> "already applied"
            is ApplyResult.Rejected -> "rejected attempts=${result.attempts}"
            is ApplyResult.Failure -> "failed reason=${result.reason} message=${result.message}"
        }
        if (result is ApplyResult.Failure || result is ApplyResult.Rejected) {
            log.w(TAG, "$deviceName: $message")
            android.util.Log.w("CodecMgr", "$deviceName: $message")
        } else {
            log.i(TAG, "$deviceName: $message")
            android.util.Log.i("CodecMgr", "$deviceName: $message")
        }
        _events.value = ApplyEvent(
            timestamp = System.currentTimeMillis(),
            mac = mac,
            deviceName = deviceName,
            quality = quality,
            result = result,
            source = source,
        )
    }

    private suspend fun waitForA2dp(device: android.bluetooth.BluetoothDevice): Boolean {
        val deadline = SystemClock.elapsedRealtime() + A2DP_TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (engine.isConnected(device)) return true
            delay(300)
        }
        return engine.isConnected(device)
    }

    private fun isAppVisible(): Boolean =
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    private fun scheduleRecheck(
        mac: String,
        deviceName: String,
        target: CodecTarget,
        keepQuality: Boolean,
    ) {
        scope.launch {
            val checks = if (keepQuality) 3 else 1
            repeat(checks) { index ->
                delay(if (index == 0) FIRST_RECHECK_MS else RECHECK_INTERVAL_MS)
                val device = runCatching { adapter?.getRemoteDevice(mac) }.getOrNull() ?: return@launch
                if (!engine.isConnected(device)) return@launch
                val status = engine.readStatus(device).getOrNull() ?: return@launch
                val resolved = CodecResolver.resolve(target, status, true) ?: return@launch
                val remembered = PlaybackQualityMemory.remembered(mac, resolved.codecType)
                if (!CodecResolver.matches(status, resolved, remembered)) {
                    log.i(TAG, "recheck: quality drifted for $deviceName, applying again")
                    val result = engine.apply(device, target)
                    if (result is ApplyResult.Applied || result is ApplyResult.AlreadyApplied) {
                        lastAppliedAt[mac] = System.currentTimeMillis()
                    }
                }
            }
        }
    }

    private companion object {
        const val TAG = "Coordinator"
        const val A2DP_TIMEOUT_MS = 30_000L
        const val A2DP_SETTLE_MS = 1_500L
        const val FIRST_RECHECK_MS = 6_000L
        const val RECHECK_INTERVAL_MS = 8_000L
    }
}
