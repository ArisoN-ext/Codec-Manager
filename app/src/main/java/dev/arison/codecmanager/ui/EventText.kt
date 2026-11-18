package dev.arison.codecmanager.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.arison.codecmanager.ApplyEvent
import dev.arison.codecmanager.R
import dev.arison.codecmanager.bluetooth.ApplyResult

/** In-app result text, shown above the floating navigation bar. */
@Composable
fun eventText(event: ApplyEvent): String {
    val detail = when (val result = event.result) {
        is ApplyResult.Applied -> if (result.unverifiedQuality) {
            stringResource(R.string.home_apply_unverified)
        } else {
            event.quality?.bitrateLabel ?: stringResource(R.string.home_apply_success)
        }

        is ApplyResult.AlreadyApplied -> stringResource(R.string.home_apply_noop)
        is ApplyResult.Rejected -> stringResource(R.string.error_rejected)
        is ApplyResult.Failure -> stringResource(result.reason.stringRes())
    }
    return stringResource(R.string.notification_applied, event.deviceName, detail)
}
