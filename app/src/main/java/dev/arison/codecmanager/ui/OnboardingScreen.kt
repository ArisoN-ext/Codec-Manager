package dev.arison.codecmanager.ui

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.arison.codecmanager.R
import dev.arison.codecmanager.ui.components.DeviceAvatar
import dev.arison.codecmanager.ui.components.PermissionCard
import dev.arison.codecmanager.ui.components.SectionTitle
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val titleRes: Int,
    val bodyRes: Int,
    val icon: ImageVector,
)

private val pages = listOf(
    OnboardingPage(R.string.onboarding_title_1, R.string.onboarding_body_1, Icons.Rounded.GraphicEq),
    OnboardingPage(R.string.onboarding_title_2, R.string.onboarding_body_2, Icons.Rounded.Bluetooth),
    OnboardingPage(R.string.onboarding_title_3, R.string.onboarding_body_3, Icons.Rounded.Headphones),
    OnboardingPage(R.string.onboarding_title_4, R.string.onboarding_body_4, Icons.Rounded.CheckCircle),
)

@Composable
fun OnboardingScreen(state: UiState, viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val bluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.refreshNow() }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.refreshNow() }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) { index ->
                when (index) {
                    1 -> PermissionsPage(
                        state = state,
                        onBluetooth = { bluetoothLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) },
                        onNotifications = {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                        onBattery = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        },
                    )
                    2 -> AssociationPage(state = state, viewModel = viewModel)
                    else -> IntroPage(pages[index])
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(pages.size) { index ->
                        Box(
                            modifier = Modifier
                                .size(if (pagerState.currentPage == index) 22.dp else 8.dp, 8.dp)
                                .background(
                                    color = if (pagerState.currentPage == index) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    shape = CircleShape,
                                ),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = { viewModel.completeOnboarding() },
                ) {
                    Text(stringResource(R.string.onboarding_skip))
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
            ) {
                if (pagerState.currentPage > 0) {
                    FilledTonalButton(
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.onboarding_back))
                    }
                }
                Button(
                    onClick = {
                        if (pagerState.currentPage == pages.lastIndex) {
                            viewModel.completeOnboarding()
                        } else {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        stringResource(
                            if (pagerState.currentPage == pages.lastIndex) {
                                R.string.onboarding_finish
                            } else {
                                R.string.onboarding_next
                            },
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun IntroPage(page: OnboardingPage) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .size(148.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(72.dp),
            )
        }
        Spacer(Modifier.height(32.dp))
        Text(
            text = stringResource(page.titleRes),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(page.bodyRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PermissionsPage(
    state: UiState,
    onBluetooth: () -> Unit,
    onNotifications: () -> Unit,
    onBattery: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.onboarding_title_2),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_body_2),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        PermissionCard(
            title = stringResource(R.string.permission_bluetooth_title),
            body = stringResource(R.string.permission_bluetooth_body),
            granted = state.diagnostics.bluetoothPermission,
            actionLabel = stringResource(R.string.permission_bluetooth_grant),
            onAction = onBluetooth,
        )
        Spacer(Modifier.height(12.dp))
        PermissionCard(
            title = stringResource(R.string.permission_notifications_title),
            body = stringResource(R.string.permission_notifications_body),
            granted = state.diagnostics.notificationsPermission,
            actionLabel = stringResource(R.string.permission_notifications_grant),
            onAction = onNotifications,
        )
        Spacer(Modifier.height(12.dp))
        PermissionCard(
            title = stringResource(R.string.permission_battery_title),
            body = stringResource(R.string.permission_battery_body),
            granted = !state.diagnostics.batteryOptimized,
            actionLabel = stringResource(R.string.permission_battery_grant),
            onAction = onBattery,
        )
    }
}

@Composable
private fun AssociationPage(state: UiState, viewModel: MainViewModel) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.assoc_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.assoc_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        if (!state.diagnostics.bluetoothPermission) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(
                    text = stringResource(R.string.error_bluetooth_permission),
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            return@Column
        }
        if (state.pairedDevices.isEmpty()) {
            Text(
                text = stringResource(R.string.assoc_no_paired),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        SectionTitle(stringResource(R.string.assoc_pick))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.pairedDevices.forEach { device ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
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
                            Text(
                                text = device.mac,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (device.associated) {
                            Text(
                                text = stringResource(R.string.assoc_already),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            FilledTonalButton(onClick = { viewModel.associate(device.mac) }) {
                                Text(stringResource(R.string.assoc_request))
                            }
                        }
                    }
                }
            }
        }
    }
}
