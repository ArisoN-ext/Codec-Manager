package dev.arison.codecmanager.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.arison.codecmanager.R
import dev.arison.codecmanager.bluetooth.BitsPerSample
import dev.arison.codecmanager.bluetooth.ChannelModes
import dev.arison.codecmanager.bluetooth.LdacQuality
import dev.arison.codecmanager.bluetooth.SampleRates
import dev.arison.codecmanager.data.LogEntry
import dev.arison.codecmanager.ui.components.bitsLabel
import dev.arison.codecmanager.ui.components.channelsLabel
import dev.arison.codecmanager.ui.components.CodecStat
import dev.arison.codecmanager.ui.components.SectionTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(state: UiState, viewModel: MainViewModel) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.diagnostics_title), fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = {
                        val intent = Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, viewModel.exportLog())
                        runCatching {
                            context.startActivity(Intent.createChooser(intent, null))
                        }
                    }) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.diag_export))
                    }
                    IconButton(onClick = viewModel::clearLog) {
                        Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.diag_clear))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            item { DeviceInfoCard(state) }
            item { ChecksCard(state) }

            item {
                SectionTitle(stringResource(R.string.diag_current_status))
            }
            item { StatusCard(state) }

            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SectionTitle(
                        text = stringResource(R.string.diag_log),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = viewModel::testApply,
                        enabled = state.activeDevice != null && !state.applying,
                    ) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.diag_run_test))
                    }
                }
            }

            if (state.log.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.diag_log_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(state.log.reversed(), key = { "${it.timestamp}-${it.tag}-${it.message.hashCode()}" }) { entry ->
                    LogRow(entry)
                }
            }

            item { Spacer(Modifier.height(112.dp)) }
        }
    }
}

@Composable
private fun DeviceInfoCard(state: UiState) {
    val diagnostics = state.diagnostics
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Science,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = diagnostics.manufacturer,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(8.dp))
            InfoLine(stringResource(R.string.diag_android), diagnostics.androidVersion)
            InfoLine(stringResource(R.string.diag_privileged), yesNo(diagnostics.privileged))
            InfoLine("A2DP API", diagnostics.apiDiagnostics)
            InfoLine(
                stringResource(R.string.diag_associated_count),
                diagnostics.associatedCount.toString(),
            )
            InfoLine(
                stringResource(R.string.home_connected),
                diagnostics.connectedName ?: stringResource(R.string.diag_no_device),
            )
        }
    }
}

@Composable
private fun ChecksCard(state: UiState) {
    val diagnostics = state.diagnostics
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            CheckRow(stringResource(R.string.diag_check_bluetooth), diagnostics.bluetoothPermission)
            CheckRow(stringResource(R.string.diag_check_adapter), diagnostics.adapterOn)
            CheckRow(stringResource(R.string.diag_check_api), diagnostics.apiAvailable)
            CheckRow(
                stringResource(R.string.diag_check_association),
                diagnostics.associatedCount > 0,
            )
            CheckRow(stringResource(R.string.diag_check_connected), diagnostics.connectedName != null)
            CheckRow(
                stringResource(R.string.diag_check_battery),
                !diagnostics.batteryOptimized,
                unknown = false,
            )
        }
    }
}

@Composable
private fun StatusCard(state: UiState) {
    val active = state.activeDevice
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (active == null) {
            Text(
                text = state.activeError?.let { stringResource(it.stringRes()) }
                    ?: stringResource(R.string.diag_no_device),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            val current = active.status.current
            val quality = current.ldacQuality
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = active.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    CodecStat(stringResource(R.string.home_codec), current.kind.displayName)
                    CodecStat(
                        stringResource(R.string.home_sample_rate),
                        SampleRates.format(current.sampleRateHz),
                    )
                    CodecStat(
                        stringResource(R.string.home_bit_depth),
                        bitsLabel(current.bitsPerSample),
                    )
                    CodecStat(
                        stringResource(R.string.home_channels),
                        channelsLabel(current.channelMode),
                    )
                }
                if (current.kind == dev.arison.codecmanager.bluetooth.CodecKind.LDAC) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${
                            stringResource(R.string.home_quality)
                        }: ${qualityName(quality)} (${quality.bitrateLabel})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CheckRow(title: String, ok: Boolean, unknown: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Icon(
            imageVector = if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.Error,
            contentDescription = null,
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(text = title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            text = stringResource(
                when {
                    unknown -> R.string.diag_unknown
                    ok -> R.string.diag_ok
                    else -> R.string.diag_fail
                },
            ),
            style = MaterialTheme.typography.labelMedium,
            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.4f),
        )
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    val color = when (entry.level) {
        "E" -> MaterialTheme.colorScheme.error
        "W" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = entry.level,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.width(16.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.tag,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = entry.message, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun qualityName(quality: LdacQuality): String = stringResource(
    when (quality) {
        LdacQuality.AUDIO_QUALITY -> R.string.quality_audio
        LdacQuality.BALANCED -> R.string.quality_balanced
        LdacQuality.CONNECTION -> R.string.quality_connection
        LdacQuality.ADAPTIVE -> R.string.quality_adaptive
        LdacQuality.DEFAULT -> R.string.quality_default
    },
)

@Composable
private fun yesNo(value: Boolean): String =
    stringResource(if (value) R.string.common_yes else R.string.common_no)
