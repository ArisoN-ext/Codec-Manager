package dev.arison.codecmanager.data

import dev.arison.codecmanager.bluetooth.CodecTarget
import dev.arison.codecmanager.bluetooth.LdacQuality
import dev.arison.codecmanager.bluetooth.SampleRateMode
import kotlinx.serialization.Serializable

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val autoSwitch: Boolean = true,
    val startOnBoot: Boolean = true,
    val notifications: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val reduceMotion: Boolean = false,
    val languageTag: String? = null,
    val onboardingDone: Boolean = false,
)

@Serializable
data class TargetDto(
    val codecType: Int? = null,
    val ldacQuality: Long = LdacQuality.AUDIO_QUALITY.code,
    val sampleRateMode: String = SampleRateMode.MAX.name,
    val sampleRateMask: Int? = null,
) {
    fun toTarget(): CodecTarget = CodecTarget(
        codecType = codecType,
        ldacQuality = LdacQuality.fromCode(codecSpecificOrQuality()),
        sampleRateMode = runCatching { SampleRateMode.valueOf(sampleRateMode) }
            .getOrDefault(SampleRateMode.MAX),
        sampleRateMask = sampleRateMask,
    )

    private fun codecSpecificOrQuality(): Long =
        LdacQuality.entries.firstOrNull { it.code == ldacQuality }?.code ?: LdacQuality.AUDIO_QUALITY.code

    companion object {
        fun from(target: CodecTarget): TargetDto = TargetDto(
            codecType = target.codecType,
            ldacQuality = target.ldacQuality.code,
            sampleRateMode = target.sampleRateMode.name,
            sampleRateMask = target.sampleRateMask,
        )
    }
}

@Serializable
data class DeviceProfile(
    val mac: String,
    val name: String,
    val enabled: Boolean = true,
    val applyOnConnect: Boolean = true,
    val keepQuality: Boolean = false,
    val target: TargetDto = TargetDto(),
) {
    fun codecTarget(): CodecTarget = target.toTarget()
}
