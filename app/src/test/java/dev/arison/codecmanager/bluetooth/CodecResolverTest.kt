package dev.arison.codecmanager.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodecResolverTest {

    private val ldacCapabilities = CodecCapabilitiesSnapshot(
        codecType = CodecIds.LDAC,
        extendedCodecTypeId = null,
        sampleRatesMask = SampleRates.HZ_44100 or SampleRates.HZ_48000 or
            SampleRates.HZ_88200 or SampleRates.HZ_96000,
        bitsPerSampleMask = BitsPerSample.SIXTEEN or BitsPerSample.TWENTY_FOUR or BitsPerSample.THIRTY_TWO,
        channelModesMask = ChannelModes.STEREO,
    )

    private fun status(
        currentSpecific1: Long = 0,
        sampleRate: Int = SampleRates.HZ_96000,
    ) = CodecStatusSnapshot(
        current = CodecConfigSnapshot(
            codecType = CodecIds.LDAC,
            codecName = "LDAC",
            extendedCodecTypeId = null,
            extendedCodecName = null,
            priority = 5001,
            sampleRate = sampleRate,
            bitsPerSample = BitsPerSample.THIRTY_TWO,
            channelMode = ChannelModes.STEREO,
            specific1 = currentSpecific1,
            specific2 = 0,
            specific3 = 0,
            specific4 = 0,
        ),
        selectable = emptyList(),
        capabilities = listOf(ldacCapabilities),
    )

    @Test
    fun `default target selects maximum sample rate and audio quality`() {
        val resolved = CodecResolver.resolve(CodecTarget.Default, status())

        assertNotNull(resolved)
        assertEquals(CodecIds.LDAC, resolved!!.codecType)
        assertEquals(SampleRates.HZ_96000, resolved.sampleRate)
        assertEquals(BitsPerSample.THIRTY_TWO, resolved.bitsPerSample)
        assertEquals(ChannelModes.STEREO, resolved.channelMode)
        assertEquals(LdacQuality.AUDIO_QUALITY.code, resolved.specific1)
    }

    @Test
    fun `auto sample rate keeps the current one`() {
        val resolved = CodecResolver.resolve(
            CodecTarget(sampleRateMode = SampleRateMode.AUTO),
            status(sampleRate = SampleRates.HZ_48000),
        )

        assertEquals(SampleRates.HZ_48000, resolved!!.sampleRate)
    }

    @Test
    fun `maximum sample rate respects capabilities`() {
        val limited = status().copy(
            capabilities = listOf(ldacCapabilities.copy(sampleRatesMask = SampleRates.HZ_44100 or SampleRates.HZ_48000)),
        )
        val resolved = CodecResolver.resolve(CodecTarget.Default, limited)

        assertEquals(SampleRates.HZ_48000, resolved!!.sampleRate)
    }

    @Test
    fun `non-ldac device is skipped for the default target`() {
        val aac = status().copy(
            current = status().current.copy(codecType = CodecIds.AAC, codecName = "AAC"),
        )

        assertNull(CodecResolver.resolve(CodecTarget.Default, aac, deviceIsLdacOnly = true))
    }

    @Test
    fun `explicit codec target is not skipped`() {
        val aac = status().copy(
            current = status().current.copy(codecType = CodecIds.AAC, codecName = "AAC"),
        )
        val resolved = CodecResolver.resolve(
            CodecTarget(codecType = CodecIds.LDAC),
            aac,
            deviceIsLdacOnly = true,
        )

        assertNotNull(resolved)
        assertEquals(CodecIds.LDAC, resolved!!.codecType)
    }

    @Test
    fun `matching accepts zero specific1 with remembered quality`() {
        val resolved = ResolvedTarget(
            codecType = CodecIds.LDAC,
            sampleRate = SampleRates.HZ_96000,
            bitsPerSample = BitsPerSample.THIRTY_TWO,
            channelMode = ChannelModes.STEREO,
            specific1 = LdacQuality.AUDIO_QUALITY.code,
        )

        assertTrue(CodecResolver.matches(status(0), resolved, rememberedQuality = 1000))
        assertFalse(CodecResolver.matches(status(0), resolved, rememberedQuality = null))
        assertFalse(CodecResolver.matches(status(1001), resolved, rememberedQuality = 1001))
        assertTrue(CodecResolver.matches(status(1000), resolved, rememberedQuality = null))
    }

    @Test
    fun `primer picks a different sample rate`() {
        val base = status()
        val resolved = CodecResolver.resolve(CodecTarget.Default, base)!!
        val primer = CodecResolver.pickPrimer(base, resolved)

        assertNotNull(primer)
        assertEquals(SampleRates.HZ_88200, primer!!.sampleRate)
        assertEquals(resolved.bitsPerSample, primer.bitsPerSample)
    }

    @Test
    fun `quality presets round trip through target dto`() {
        val target = CodecTarget(
            ldacQuality = LdacQuality.BALANCED,
            sampleRateMode = SampleRateMode.AUTO,
        )
        val restored = dev.arison.codecmanager.data.TargetDto.from(target).toTarget()

        assertEquals(LdacQuality.BALANCED, restored.ldacQuality)
        assertEquals(SampleRateMode.AUTO, restored.sampleRateMode)
    }
}
