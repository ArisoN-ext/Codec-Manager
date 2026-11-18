package dev.arison.codecmanager.ui

import androidx.annotation.StringRes
import dev.arison.codecmanager.R
import dev.arison.codecmanager.bluetooth.FailureReason

@StringRes
fun FailureReason.stringRes(): Int = when (this) {
    FailureReason.NOT_ASSOCIATED -> R.string.error_not_associated
    FailureReason.PERMISSION_DENIED -> R.string.error_permission
    FailureReason.HIDDEN_API_UNAVAILABLE -> R.string.error_hidden_api
    FailureReason.ADAPTER_OFF -> R.string.error_adapter_off
    FailureReason.NOT_CONNECTED -> R.string.error_not_connected
    FailureReason.CODEC_NOT_SUPPORTED -> R.string.error_hidden_api
    FailureReason.REJECTED -> R.string.error_rejected
    FailureReason.TIMEOUT -> R.string.error_timeout
    FailureReason.UNKNOWN -> R.string.error_unknown
}
