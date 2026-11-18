package dev.arison.codecmanager.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.arison.codecmanager.R
import dev.arison.codecmanager.bluetooth.BitsPerSample
import dev.arison.codecmanager.bluetooth.ChannelModes

@Composable
fun bitsLabel(mask: Int): String = when (mask) {
    BitsPerSample.SIXTEEN -> stringResource(R.string.bits_16)
    BitsPerSample.TWENTY_FOUR -> stringResource(R.string.bits_24)
    BitsPerSample.THIRTY_TWO -> stringResource(R.string.bits_32)
    BitsPerSample.TWENTY_FOUR_OR_THIRTY_TWO -> stringResource(R.string.bits_24_32)
    else -> "—"
}

@Composable
fun channelsLabel(mode: Int): String = when (mode) {
    ChannelModes.MONO -> stringResource(R.string.channels_mono)
    ChannelModes.STEREO -> stringResource(R.string.channels_stereo)
    ChannelModes.DUAL -> stringResource(R.string.channels_dual)
    else -> "—"
}
