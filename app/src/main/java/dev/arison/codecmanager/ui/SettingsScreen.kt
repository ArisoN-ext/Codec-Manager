package dev.arison.codecmanager.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.arison.codecmanager.BuildConfig
import dev.arison.codecmanager.R
import dev.arison.codecmanager.data.ThemeMode
import dev.arison.codecmanager.ui.components.SectionTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: UiState, viewModel: MainViewModel) {
    val context = LocalContext.current
    var themeDialog by remember { mutableStateOf(false) }
    var languageDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) })
        },
    ) { padding ->
        LazyColumn(
            contentPadding = padding,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            item { SectionTitle(stringResource(R.string.settings_automation)) }
            item {
                SettingsCard {
                    SwitchRow(
                        title = stringResource(R.string.settings_auto_switch),
                        subtitle = stringResource(R.string.settings_auto_switch_desc),
                        checked = state.settings.autoSwitch,
                        onCheckedChange = viewModel::setAutoSwitch,
                    )
                    HorizontalDivider()
                    SwitchRow(
                        title = stringResource(R.string.settings_start_on_boot),
                        checked = state.settings.startOnBoot,
                        onCheckedChange = viewModel::setStartOnBoot,
                    )
                    HorizontalDivider()
                    SwitchRow(
                        title = stringResource(R.string.settings_notify),
                        subtitle = stringResource(R.string.settings_notify_desc),
                        checked = state.settings.notifications,
                        onCheckedChange = viewModel::setNotifications,
                    )
                }
            }

            item { SectionTitle(stringResource(R.string.settings_theme)) }
            item {
                SettingsCard {
                    NavRow(
                        icon = Icons.Rounded.Palette,
                        title = stringResource(R.string.settings_theme),
                        value = when (state.settings.themeMode) {
                            ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
                            ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                            ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
                        },
                        onClick = { themeDialog = true },
                    )
                    HorizontalDivider()
                    SwitchRow(
                        title = stringResource(R.string.settings_dynamic_color),
                        subtitle = stringResource(R.string.settings_dynamic_color_desc),
                        checked = state.settings.dynamicColor,
                        onCheckedChange = viewModel::setDynamicColor,
                    )
                    HorizontalDivider()
                    SwitchRow(
                        title = stringResource(R.string.settings_reduce_motion),
                        checked = state.settings.reduceMotion,
                        onCheckedChange = viewModel::setReduceMotion,
                    )
                    HorizontalDivider()
                    NavRow(
                        icon = Icons.Rounded.Language,
                        title = stringResource(R.string.settings_language),
                        value = when (state.settings.languageTag) {
                            "ru" -> "Русский"
                            "en" -> "English"
                            else -> stringResource(R.string.settings_language_system)
                        },
                        onClick = { languageDialog = true },
                    )
                }
            }

            item { SectionTitle(stringResource(R.string.settings_about)) }
            item {
                SettingsCard {
                    NavRow(
                        icon = Icons.Rounded.Bluetooth,
                        title = stringResource(R.string.settings_open_bluetooth),
                        value = "",
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        },
                    )
                    HorizontalDivider()
                    NavRow(
                        icon = Icons.Rounded.BatteryChargingFull,
                        title = stringResource(R.string.settings_battery_optimization),
                        value = if (state.diagnostics.batteryOptimized) {
                            stringResource(R.string.diag_fail)
                        } else {
                            stringResource(R.string.diag_ok)
                        },
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        },
                    )
                    HorizontalDivider()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                text = stringResource(
                                    R.string.settings_version,
                                    BuildConfig.VERSION_NAME,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.settings_about_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.settings_tile_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }

            item { Spacer(Modifier.height(112.dp)) }
        }
    }

    if (themeDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = listOf(
                ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
                ThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
                ThemeMode.DARK to stringResource(R.string.settings_theme_dark),
            ),
            selected = state.settings.themeMode,
            onSelect = {
                viewModel.setThemeMode(it)
                themeDialog = false
            },
            onDismiss = { themeDialog = false },
        )
    }

    if (languageDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_language),
            options = listOf(
                null to stringResource(R.string.settings_language_system),
                "en" to "English",
                "ru" to "Русский",
            ),
            selected = state.settings.languageTag,
            onSelect = {
                viewModel.setLanguage(it)
                languageDialog = false
            },
            onDismiss = { languageDialog = false },
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column { content() }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    subtitle: String? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
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

@Composable
private fun NavRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (value.isNotEmpty()) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(vertical = 10.dp),
                    ) {
                        RadioButton(selected = value == selected, onClick = { onSelect(value) })
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        },
    )
}
