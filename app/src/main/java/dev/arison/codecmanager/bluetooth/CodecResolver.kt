package dev.arison.codecmanager.bluetooth

import kotlin.math.abs

/** Pure target resolution and verification helpers. */
object CodecResolver {

    fun resolve(
        target: CodecTarget,
        status: CodecStatusSnapshot,
        deviceIsLdacOnly: Boolean = false,
    ): ResolvedTarget? {
        val current = status.current
        val codecType = target.codecType ?: current.codecType
        val capabilities = status.capabilitiesFor(
            current.copy(
                codecType = codecType,
                extendedCodecTypeId = if (codecType == current.codecType) current.extendedCodecTypeId else null,
            ),
        )

        val sampleRate = when (target.sampleRateMode) {
            SampleRateMode.AUTO -> current.sampleRate
            SampleRateMode.MAX -> {
                val mask = capabilities?.sampleRatesMask ?: 0
                SampleRates.highest(mask).takeIf { it != SampleRates.DEFAULT } ?: current.sampleRate
            }
            SampleRateMode.FIXED -> {
                val requested = target.sampleRateMask ?: current.sampleRate
                val mask = capabilities?.sampleRatesMask ?: 0
                if (mask == 0 || mask and requested != 0) requested else SampleRates.highest(mask)
            }
        }

        if (deviceIsLdacOnly && target.codecType == null && current.kind != CodecKind.LDAC) {
            return null
        }

        val specific1 = when (codecType) {
            CodecIds.LDAC -> target.ldacQuality.code
            else -> current.specific1
        }

        return ResolvedTarget(
            codecType = codecType,
            sampleRate = sampleRate,
            bitsPerSample = current.bitsPerSample,
            channelMode = current.channelMode,
            specific1 = specific1,
        )
    }

    fun matches(status: CodecStatusSnapshot, resolved: ResolvedTarget, rememberedQuality: Long?): Boolean =
        resolved.matchesConfig(status.current) &&
            resolved.qualityMatches(status.current, rememberedQuality)

    /**
     * A config that differs from the resolved target on exactly one axis. Setting it first forces
     * the stack to renegotiate instead of silently ignoring an unchanged preference.
     */
    fun pickPrimer(
        status: CodecStatusSnapshot,
        resolved: ResolvedTarget,
    ): ResolvedTarget? {
        val capabilities = status.capabilitiesFor(
            status.current.copy(codecType = resolved.codecType),
        ) ?: return null

        val targetHz = SampleRates.toHz(resolved.sampleRate)
        val candidateRates = SampleRates.supported(capabilities.sampleRatesMask)
            .filter { it != resolved.sampleRate }
        val nearestRate = candidateRates.minByOrNull { abs(SampleRates.toHz(it) - targetHz) }
        if (nearestRate != null) {
            return resolved.copy(sampleRate = nearestRate)
        }

        val candidateBits = BitsPerSample.supported(capabilities.bitsPerSampleMask)
            .filter { it != resolved.bitsPerSample }
        if (candidateBits.isNotEmpty()) {
            return resolved.copy(bitsPerSample = candidateBits.first())
        }

        val candidateChannels = ChannelModes.supported(capabilities.channelModesMask)
            .filter { it != resolved.channelMode }
        if (candidateChannels.isNotEmpty()) {
            return resolved.copy(channelMode = candidateChannels.first())
        }

        return null
    }

    fun highestSupportedConfig(status: CodecStatusSnapshot): CodecConfigSnapshot? =
        status.selectable.maxByOrNull { SampleRates.toHz(it.sampleRate) }
}
