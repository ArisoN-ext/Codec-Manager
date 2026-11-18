package dev.arison.codecmanager.ui

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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.arison.codecmanager.R
import dev.arison.codecmanager.bluetooth.BitsPerSample
import dev.arison.codecmanager.bluetooth.ChannelModes
import dev.arison.codecmanager.bluetooth.LdacQuality
import dev.arison.codecmanager.bluetooth.SampleRates
import dev.arison.codecmanager.ui.components.bitsLabel
import dev.arison.codecmanager.ui.components.channelsLabel
import dev.arison.codecmanager.ui.components.CodecStat
import dev.arison.codecmanager.ui.components.DeviceAvatar
import dev.arison.codecmanager.ui.components.QualitySelector
import dev.arison.codecmanager.ui.components.SectionTitle
import dev.arison.codecmanager.ui.components.StatusPill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(state: UiState, viewModel: MainViewModel) {
    var showAssociate by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.home_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(
                                if (state.settings.autoSwitch) {
                                    R.string.home_status_service_on
                                } else {
                                    R.string.home_status_service_off
                                },
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refreshNow) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.home_read_status))
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
            item { ActiveDeviceCard(state, viewModel) }

            item {
                SectionTitle(
                    text = stringResource(R.string.home_devices_title),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            val associated = state.pairedDevices.filter { it.associated }
            if (associated.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.home_devices_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                items(associated, key = { it.mac }) { device ->
                    val profile = state.profiles.firstOrNull {
                        it.mac.equals(device.mac, ignoreCase = true)
                    }
                    DeviceRow(
                        device = device,
                        applyOnConnect = profile?.applyOnConnect ?: true,
                        onApplyOnConnectChange = { enabled ->
                            viewModel.setDeviceAutoApply(device.mac, device.name, enabled)
                        },
                    )
                }
            }

            item {
                OutlinedButton(
                    onClick = { showAssociate = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.home_devices_add))
                }
            }

            item { Spacer(Modifier.height(112.dp)) }
        }
    }

    if (showAssociate) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showAssociate = false },
            sheetState = sheetState,
        ) {
            AssociateDeviceSheet(
                state = state,
                onAssociate = { mac ->
                    viewModel.associate(mac)
                    showAssociate = false
                },
                onDismiss = { showAssociate = false },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ActiveDeviceCard(state: UiState, viewModel: MainViewModel) {
    val active = state.activeDevice
    if (active == null) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Headphones,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.home_no_device_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                state.activeError?.let { reason ->
                    Text(
                        text = stringResource(reason.stringRes()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    text = stringResource(R.string.home_no_device_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    val current = active.status.current
    val quality = current.ldacQuality.takeIf { it != LdacQuality.DEFAULT }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DeviceAvatar(active.name)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_active_device),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = active.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                StatusPill(text = stringResource(R.string.home_connected), active = true)
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                CodecStat(
                    label = stringResource(R.string.home_codec),
                    value = current.kind.displayName,
                )
                CodecStat(
                    label = stringResource(R.string.home_sample_rate),
                    value = SampleRates.format(current.sampleRateHz),
                )
                CodecStat(
                    label = stringResource(R.string.home_bit_depth),
                    value = bitsLabel(current.bitsPerSample),
                )
                CodecStat(
                    label = stringResource(R.string.home_channels),
                    value = channelsLabel(current.channelMode),
                )
            }

            if (current.kind == dev.arison.codecmanager.bluetooth.CodecKind.LDAC) {
                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f))
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.home_quality),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.height(8.dp))
                QualitySelector(
                    selected = quality,
                    enabled = !state.applying,
                    onSelect = { selected -> viewModel.applyQuality(active.mac, selected) },
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.home_quality_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                )
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = { viewModel.applyQuality(active.mac, quality ?: LdacQuality.AUDIO_QUALITY) },
                    enabled = !state.applying,
                ) {
                    if (state.applying) {
                        LoadingIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(
                            if (state.applying) R.string.home_applying else R.string.home_apply,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: PairedDeviceUi,
    applyOnConnect: Boolean,
    onApplyOnConnectChange: (Boolean) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            DeviceAvatar(device.name)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Bluetooth,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (device.connected) {
                            stringResource(R.string.home_connected)
                        } else {
                            device.mac
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(R.string.home_device_apply_on_connect),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Switch(checked = applyOnConnect, onCheckedChange = onApplyOnConnectChange)
            }
        }
    }
}

@Composable
private fun AssociateDeviceSheet(
    state: UiState,
    onAssociate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val candidates = state.pairedDevices.filter { !it.associated }
    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.assoc_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.assoc_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        if (candidates.isEmpty()) {
            Text(
                text = stringResource(R.string.assoc_no_paired),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                candidates.forEach { device ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        DeviceAvatar(device.name)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(device.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                device.mac,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FilledTonalButton(onClick = { onAssociate(device.mac) }) {
                            Text(stringResource(R.string.assoc_request))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}
