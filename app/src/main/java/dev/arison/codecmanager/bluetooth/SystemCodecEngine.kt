package dev.arison.codecmanager.bluetooth

import android.bluetooth.BluetoothDevice
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Applies a [CodecTarget] with verification and a small ladder of forced reconfigurations:
 *
 * 1. send the target configuration and poll `getCodecStatus`;
 * 2. if unchanged, send a "primer" that differs on one axis, then the target again;
 * 3. if still unchanged, wait and retry the target once more.
 */
class SystemCodecEngine(private val api: A2dpCodecApi) : CodecEngine {

    private val mutex = Mutex()

    override fun isAvailable(): Boolean = api.hasRequiredMethods()

    override fun diagnostics(): String = api.diagnostics()

    override suspend fun readStatus(device: BluetoothDevice): Result<CodecStatusSnapshot> =
        try {
            Result.success(api.readStatus(device))
        } catch (failure: CodecFailure) {
            Result.failure(failure)
        } catch (throwable: Throwable) {
            Result.failure(CodecFailure(FailureReason.UNKNOWN, throwable.message, throwable))
        }

    override suspend fun isConnected(device: BluetoothDevice): Boolean = api.isConnected(device)

    override suspend fun connectedDevices(): List<BluetoothDevice> = api.connectedDevices()

    override suspend fun apply(
        device: BluetoothDevice,
        target: CodecTarget,
        policy: ApplyPolicy,
    ): ApplyResult = mutex.withLock {
        val initial = try {
            api.readStatus(device)
        } catch (failure: CodecFailure) {
            return@withLock ApplyResult.Failure(failure.reason, failure.message)
        }

        val resolved = CodecResolver.resolve(
            target = target,
            status = initial,
            deviceIsLdacOnly = target.codecType == null,
        ) ?: return@withLock ApplyResult.Failure(
            FailureReason.CODEC_NOT_SUPPORTED,
            "The active codec is not LDAC, nothing to tune",
            initial,
        )

        if (initial.selectable.none { it.codecType == resolved.codecType }) {
            return@withLock ApplyResult.Failure(
                FailureReason.CODEC_NOT_SUPPORTED,
                "Codec ${CodecKind.of(resolved.codecType).displayName} is not selectable",
                initial,
            )
        }

        val remembered = PlaybackQualityMemory.remembered(device.address, resolved.codecType)
        if (CodecResolver.matches(initial, resolved, remembered)) {
            return@withLock ApplyResult.AlreadyApplied(initial)
        }

        val targetConfig = api.buildConfig(initial.current, resolved)
        var attempts = 0

        try {
            api.setCodecConfig(device, targetConfig)
            attempts++
        } catch (failure: CodecFailure) {
            return@withLock ApplyResult.Failure(
                reason = failure.reason,
                message = failure.message,
                status = initial,
                attempts = attempts,
            )
        }

        var verification = awaitVerification(device, resolved, remembered, policy)
        attempts += verification.reads

        if (!verification.matched && policy.allowPrimer) {
            val primer = CodecResolver.pickPrimer(initial, resolved)
            if (primer != null) {
                Log.d(TAG, "primer=${primer.sampleRate}/${primer.bitsPerSample} target=${resolved.sampleRate}")
                try {
                    api.setCodecConfig(device, api.buildConfig(initial.current, primer))
                    attempts++
                    delay(policy.primerGapMs)
                    api.setCodecConfig(device, targetConfig)
                    attempts++
                } catch (failure: CodecFailure) {
                    return@withLock ApplyResult.Failure(
                        reason = failure.reason,
                        message = failure.message,
                        status = verification.status ?: initial,
                        attempts = attempts,
                    )
                }
                verification = awaitVerification(device, resolved, remembered, policy)
                attempts += verification.reads
            }
        }

        if (!verification.matched && policy.allowTwoStep) {
            delay(policy.settleMs)
            try {
                api.setCodecConfig(device, targetConfig)
                attempts++
            } catch (failure: CodecFailure) {
                return@withLock ApplyResult.Failure(
                    reason = failure.reason,
                    message = failure.message,
                    status = verification.status ?: initial,
                    attempts = attempts,
                )
            }
            verification = awaitVerification(device, resolved, remembered, policy)
            attempts += verification.reads
        }

        val finalStatus = verification.status ?: initial
        when {
            verification.matched -> {
                PlaybackQualityMemory.remember(device.address, resolved.codecType, resolved.specific1)
                ApplyResult.Applied(
                    status = finalStatus,
                    attempts = attempts,
                    unverifiedQuality = verification.unverified,
                )
            }
            verification.unverifiedQuality -> {
                PlaybackQualityMemory.remember(device.address, resolved.codecType, resolved.specific1)
                ApplyResult.Applied(
                    status = finalStatus,
                    attempts = attempts,
                    unverifiedQuality = true,
                )
            }
            else -> ApplyResult.Rejected(finalStatus, attempts)
        }
    }

    private data class Verification(
        val matched: Boolean,
        val unverified: Boolean,
        val status: CodecStatusSnapshot?,
        val reads: Int,
    ) {
        val unverifiedQuality: Boolean get() = unverified
    }

    private suspend fun awaitVerification(
        device: BluetoothDevice,
        resolved: ResolvedTarget,
        remembered: Long?,
        policy: ApplyPolicy,
    ): Verification {
        delay(policy.settleMs)
        val deadline = SystemClock.elapsedRealtime() + policy.verifyTimeoutMs
        var last: CodecStatusSnapshot? = null
        var reads = 0
        var sawUnverified = false

        while (SystemClock.elapsedRealtime() < deadline) {
            val status = try {
                api.readStatus(device)
            } catch (_: CodecFailure) {
                null
            }
            if (status != null) {
                reads++
                last = status
                if (resolved.matchesConfig(status.current)) {
                    if (resolved.qualityMatches(status.current, remembered)) {
                        return Verification(true, false, status, reads)
                    }
                    if (status.current.specific1 == 0L) {
                        sawUnverified = true
                    }
                }
            }
            delay(policy.pollMs)
        }
        return Verification(false, sawUnverified, last, reads)
    }

    private companion object {
        const val TAG = "CodecEngine"
    }
}
