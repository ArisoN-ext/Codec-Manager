package dev.arison.codecmanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import dev.arison.codecmanager.bluetooth.CodecTarget
import dev.arison.codecmanager.bluetooth.LdacQuality
import dev.arison.codecmanager.bluetooth.SampleRateMode
import dev.arison.codecmanager.data.DeviceProfile
import dev.arison.codecmanager.data.TargetDto
import dev.arison.codecmanager.ui.components.DeviceAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(state: UiState, viewModel: MainViewModel) {
    var editing by remember { mutableStateOf<DeviceProfile?>(null) }
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profiles_title), fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { creating = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.profiles_add))
                    }
                },
            )
        },
    ) { padding ->
        if (state.profiles.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(padding)
                    .padding(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.profiles_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.profiles_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { creating = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.profiles_add))
                }
            }
        } else {
            LazyColumn(
                contentPadding = padding,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                items(state.profiles, key = { it.mac }) { profile ->
                    ProfileCard(
                        profile = profile,
                        onClick = { editing = profile },
                        onEnabledChange = { viewModel.setProfileEnabled(profile, it) },
                        onApplyOnConnectChange = { viewModel.setApplyOnConnect(profile, it) },
                    )
                }
                item { Spacer(Modifier.height(120.dp)) }
            }
        }
    }

    if (creating || editing != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ProfileEditorSheet(
            initial = editing,
            devices = state.pairedDevices,
            sheetState = sheetState,
            onDismiss = {
                creating = false
                editing = null
            },
            onSave = { profile ->
                viewModel.saveProfile(profile)
                creating = false
                editing = null
            },
            onDelete = { profile ->
                viewModel.deleteProfile(profile.mac)
                creating = false
                editing = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileCard(
    profile: DeviceProfile,
    onClick: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onApplyOnConnectChange: (Boolean) -> Unit,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DeviceAvatar(profile.name)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = summary(profile),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = profile.enabled, onCheckedChange = onEnabledChange)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.home_device_apply_on_connect),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = profile.applyOnConnect, onCheckedChange = onApplyOnConnectChange)
            }
        }
    }
}

@Composable
private fun summary(profile: DeviceProfile): String {
    val quality = LdacQuality.fromCode(profile.target.ldacQuality)
    val qualityLabel = stringResource(
        when (quality) {
            LdacQuality.AUDIO_QUALITY -> R.string.quality_audio
            LdacQuality.BALANCED -> R.string.quality_balanced
            LdacQuality.CONNECTION -> R.string.quality_connection
            LdacQuality.ADAPTIVE -> R.string.quality_adaptive
            LdacQuality.DEFAULT -> R.string.quality_default
        },
    )
    val rateLabel = when (profile.target.sampleRateMode) {
        SampleRateMode.MAX.name -> stringResource(R.string.profile_max)
        SampleRateMode.AUTO.name -> stringResource(R.string.profile_auto)
        else -> stringResource(R.string.profile_custom)
    }
    return "LDAC · $qualityLabel · $rateLabel"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ProfileEditorSheet(
    initial: DeviceProfile?,
    devices: List<PairedDeviceUi>,
    sheetState: androidx.compose.material3.SheetState,
    onDismiss: () -> Unit,
    onSave: (DeviceProfile) -> Unit,
    onDelete: (DeviceProfile) -> Unit,
) {
    val initialDevice = initial?.let { profile ->
        devices.firstOrNull { it.mac.equals(profile.mac, ignoreCase = true) }
    } ?: devices.firstOrNull()

    var mac by remember(initial, initialDevice) { mutableStateOf(initial?.mac ?: initialDevice?.mac.orEmpty()) }
    var name by remember(initial, initialDevice) { mutableStateOf(initial?.name ?: initialDevice?.name.orEmpty()) }
    var quality by remember(initial) {
        mutableStateOf(LdacQuality.fromCode(initial?.target?.ldacQuality ?: LdacQuality.AUDIO_QUALITY.code))
    }
    var rateMode by remember(initial) {
        mutableStateOf(
            initial?.target?.sampleRateMode?.let { runCatching { SampleRateMode.valueOf(it) }.getOrNull() }
                ?: SampleRateMode.MAX,
        )
    }
    var enabled by remember(initial) { mutableStateOf(initial?.enabled ?: true) }
    var applyOnConnect by remember(initial) { mutableStateOf(initial?.applyOnConnect ?: true) }
    var keepQuality by remember(initial) { mutableStateOf(initial?.keepQuality ?: false) }
    var dropdownOpen by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.profile_edit),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            Box {
                OutlinedButton(
                    onClick = { dropdownOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = name.ifBlank { stringResource(R.string.assoc_pick) },
                        modifier = Modifier.weight(1f),
                    )
                }
                DropdownMenu(expanded = dropdownOpen, onDismissRequest = { dropdownOpen = false }) {
                    devices.forEach { device ->
                        DropdownMenuItem(
                            text = { Text(device.name) },
                            onClick = {
                                mac = device.mac
                                name = device.name
                                dropdownOpen = false
                            },
                        )
                    }
                }
            }

            Text(stringResource(R.string.profile_ldac_quality), fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    LdacQuality.AUDIO_QUALITY to R.string.quality_audio,
                    LdacQuality.BALANCED to R.string.quality_balanced,
                    LdacQuality.CONNECTION to R.string.quality_connection,
                    LdacQuality.ADAPTIVE to R.string.quality_adaptive,
                ).forEach { (option, label) ->
                    FilterChip(
                        selected = quality == option,
                        onClick = { quality = option },
                        label = { Text(stringResource(label)) },
                    )
                }
            }

            Text(stringResource(R.string.profile_sample_rate), fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = rateMode == SampleRateMode.MAX,
                    onClick = { rateMode = SampleRateMode.MAX },
                    label = { Text(stringResource(R.string.profile_max)) },
                )
                FilterChip(
                    selected = rateMode == SampleRateMode.AUTO,
                    onClick = { rateMode = SampleRateMode.AUTO },
                    label = { Text(stringResource(R.string.profile_auto)) },
                )
            }

            ToggleRow(stringResource(R.string.profile_enabled), enabled) { enabled = it }
            ToggleRow(stringResource(R.string.profile_apply_on_connect), applyOnConnect) {
                applyOnConnect = it
            }
            ToggleRow(
                stringResource(R.string.profile_lock_quality),
                keepQuality,
                subtitle = stringResource(R.string.profile_lock_quality_desc),
            ) { keepQuality = it }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (initial != null) {
                    IconButton(onClick = { onDelete(initial) }) {
                        Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.profile_delete))
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                Button(
                    onClick = {
                        if (mac.isNotBlank()) {
                            onSave(
                                DeviceProfile(
                                    mac = mac,
                                    name = name.ifBlank { mac },
                                    enabled = enabled,
                                    applyOnConnect = applyOnConnect,
                                    keepQuality = keepQuality,
                                    target = TargetDto.from(
                                        CodecTarget(
                                            codecType = null,
                                            ldacQuality = quality,
                                            sampleRateMode = rateMode,
                                        ),
                                    ),
                                ),
                            )
                        }
                    },
                    enabled = mac.isNotBlank(),
                ) {
                    Text(stringResource(R.string.profile_save))
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    checked: Boolean,
    subtitle: String? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
