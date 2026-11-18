package dev.arison.codecmanager.bluetooth

/**
 * Codec configuration model, deliberately independent from framework types so it can be
 * unit-tested and serialized.
 */
object CodecIds {
    const val SBC = 0
    const val AAC = 1
    const val APTX = 2
    const val APTX_HD = 3
    const val LDAC = 4
    const val LC3 = 5
    const val OPUS = 6
    const val UNKNOWN = -1
}

object SampleRates {
    const val DEFAULT = 0
    const val HZ_44100 = 1
    const val HZ_48000 = 2
    const val HZ_88200 = 4
    const val HZ_96000 = 8
    const val HZ_176400 = 16
    const val HZ_192000 = 32

    val ordered = listOf(HZ_44100, HZ_48000, HZ_88200, HZ_96000, HZ_176400, HZ_192000)

    fun toHz(mask: Int): Int = when (mask) {
        HZ_44100 -> 44_100
        HZ_48000 -> 48_000
        HZ_88200 -> 88_200
        HZ_96000 -> 96_000
        HZ_176400 -> 176_400
        HZ_192000 -> 192_000
        else -> 0
    }

    fun fromHz(hz: Int): Int = when (hz) {
        44_100 -> HZ_44100
        48_000 -> HZ_48000
        88_200 -> HZ_88200
        96_000 -> HZ_96000
        176_400 -> HZ_176400
        192_000 -> HZ_192000
        else -> DEFAULT
    }

    fun supported(mask: Int): List<Int> = ordered.filter { mask and it != 0 }

    fun highest(mask: Int): Int = ordered.lastOrNull { mask and it != 0 } ?: DEFAULT

    fun format(hz: Int): String = if (hz <= 0) "—" else "${hz / 1000.0}".trimEnd('0').trimEnd('.') + " kHz"
}

object BitsPerSample {
    const val DEFAULT = 0
    const val SIXTEEN = 1
    const val TWENTY_FOUR = 2
    const val THIRTY_TWO = 4
    const val TWENTY_FOUR_OR_THIRTY_TWO = 8

    fun supported(mask: Int): List<Int> =
        listOf(SIXTEEN, TWENTY_FOUR, THIRTY_TWO, TWENTY_FOUR_OR_THIRTY_TWO).filter { mask and it != 0 }

    fun format(mask: Int): String = when (mask) {
        SIXTEEN -> "16 bit"
        TWENTY_FOUR -> "24 bit"
        THIRTY_TWO -> "32 bit"
        TWENTY_FOUR_OR_THIRTY_TWO -> "24/32 bit"
        else -> "—"
    }
}

object ChannelModes {
    const val DEFAULT = 0
    const val MONO = 1
    const val STEREO = 2
    const val DUAL = 4

    fun supported(mask: Int): List<Int> = listOf(MONO, STEREO, DUAL).filter { mask and it != 0 }

    fun format(mode: Int): String = when (mode) {
        MONO -> "Mono"
        STEREO -> "Stereo"
        DUAL -> "Dual"
        else -> "—"
    }
}

enum class CodecKind(val id: Int, val displayName: String, val shortName: String) {
    SBC(CodecIds.SBC, "SBC", "SBC"),
    AAC(CodecIds.AAC, "AAC", "AAC"),
    APTX(CodecIds.APTX, "aptX", "aptX"),
    APTX_HD(CodecIds.APTX_HD, "aptX HD", "aptX HD"),
    LDAC(CodecIds.LDAC, "LDAC", "LDAC"),
    LC3(CodecIds.LC3, "LC3", "LC3"),
    OPUS(CodecIds.OPUS, "Opus", "Opus"),
    UNKNOWN(CodecIds.UNKNOWN, "Unknown", "—");

    companion object {
        fun of(id: Int): CodecKind = entries.firstOrNull { it.id == id } ?: UNKNOWN
    }
}

enum class LdacQuality(val code: Long) {
    AUDIO_QUALITY(1000L),
    BALANCED(1001L),
    CONNECTION(1002L),
    ADAPTIVE(1003L),
    DEFAULT(0L);

    val bitrateLabel: String
        get() = when (this) {
            AUDIO_QUALITY -> "990/909 kbps"
            BALANCED -> "660/606 kbps"
            CONNECTION -> "330/303 kbps"
            ADAPTIVE -> "Adaptive"
            DEFAULT -> "—"
        }

    companion object {
        fun fromCode(code: Long): LdacQuality = entries.firstOrNull { it.code == code } ?: DEFAULT
    }
}

data class CodecConfigSnapshot(
    val codecType: Int,
    val codecName: String?,
    val extendedCodecTypeId: Long?,
    val extendedCodecName: String?,
    val priority: Int,
    val sampleRate: Int,
    val bitsPerSample: Int,
    val channelMode: Int,
    val specific1: Long,
    val specific2: Long,
    val specific3: Long,
    val specific4: Long,
    /** Opaque android.bluetooth.BluetoothCodecType instance on API 35+, kept for rebuilding. */
    val extendedTypeRef: Any? = null,
) {
    val kind: CodecKind get() = CodecKind.of(codecType)
    val sampleRateHz: Int get() = SampleRates.toHz(sampleRate)
    val ldacQuality: LdacQuality get() = LdacQuality.fromCode(specific1)
}

data class CodecCapabilitiesSnapshot(
    val codecType: Int,
    val extendedCodecTypeId: Long?,
    val sampleRatesMask: Int,
    val bitsPerSampleMask: Int,
    val channelModesMask: Int,
)

data class CodecStatusSnapshot(
    val current: CodecConfigSnapshot,
    val selectable: List<CodecConfigSnapshot>,
    val capabilities: List<CodecCapabilitiesSnapshot>,
) {
    fun capabilitiesFor(config: CodecConfigSnapshot): CodecCapabilitiesSnapshot? {
        val sameCodec = capabilities.filter { it.codecType == config.codecType }
        if (sameCodec.size <= 1) return sameCodec.firstOrNull()
        return sameCodec.firstOrNull { it.extendedCodecTypeId == config.extendedCodecTypeId }
            ?: sameCodec.firstOrNull { it.extendedCodecTypeId == null }
            ?: sameCodec.first()
    }
}

enum class SampleRateMode { AUTO, MAX, FIXED }

/**
 * What a profile wants to achieve. A null [codecType] means "keep the codec that is currently
 * active" — the default profile then only adjusts LDAC quality on devices already running LDAC.
 */
data class CodecTarget(
    val codecType: Int? = null,
    val ldacQuality: LdacQuality = LdacQuality.AUDIO_QUALITY,
    val sampleRateMode: SampleRateMode = SampleRateMode.MAX,
    val sampleRateMask: Int? = null,
) {
    companion object {
        val Default: CodecTarget = CodecTarget()
    }
}

data class ResolvedTarget(
    val codecType: Int,
    val sampleRate: Int,
    val bitsPerSample: Int,
    val channelMode: Int,
    val specific1: Long,
) {
    fun matchesConfig(config: CodecConfigSnapshot): Boolean =
        config.codecType == codecType &&
            config.sampleRate == sampleRate &&
            config.bitsPerSample == bitsPerSample &&
            config.channelMode == channelMode

    fun qualityMatches(config: CodecConfigSnapshot, remembered: Long?): Boolean {
        if (specific1 == 0L) return true
        if (config.specific1 == specific1) return true
        if (config.specific1 == 0L && remembered == specific1) return true
        return false
    }
}

enum class FailureReason {
    NOT_ASSOCIATED,
    PERMISSION_DENIED,
    HIDDEN_API_UNAVAILABLE,
    ADAPTER_OFF,
    NOT_CONNECTED,
    CODEC_NOT_SUPPORTED,
    REJECTED,
    TIMEOUT,
    UNKNOWN,
}

class CodecFailure(
    val reason: FailureReason,
    override val message: String? = null,
    override val cause: Throwable? = null,
) : Exception(message, cause)

data class ApplyPolicy(
    val settleMs: Long = 300,
    val verifyTimeoutMs: Long = 3_000,
    val pollMs: Long = 200,
    val primerGapMs: Long = 1_000,
    val allowPrimer: Boolean = true,
    val allowTwoStep: Boolean = true,
) {
    companion object {
        val Default = ApplyPolicy()
    }
}

sealed interface ApplyResult {
    val status: CodecStatusSnapshot?
    val attempts: Int

    data class Applied(
        override val status: CodecStatusSnapshot,
        override val attempts: Int,
        val unverifiedQuality: Boolean = false,
    ) : ApplyResult

    data class AlreadyApplied(
        override val status: CodecStatusSnapshot,
        override val attempts: Int = 0,
    ) : ApplyResult

    data class Rejected(
        override val status: CodecStatusSnapshot?,
        override val attempts: Int,
    ) : ApplyResult

    data class Failure(
        val reason: FailureReason,
        val message: String?,
        override val status: CodecStatusSnapshot? = null,
        override val attempts: Int = 0,
    ) : ApplyResult
}
