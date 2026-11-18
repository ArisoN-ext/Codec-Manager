package dev.arison.codecmanager.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.arison.codecmanager.R
import dev.arison.codecmanager.ui.components.BottomNavItem
import dev.arison.codecmanager.ui.components.FloatingNavBar

@Composable
fun MainScaffold(state: UiState, viewModel: MainViewModel) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val items = listOf(
        BottomNavItem(R.string.nav_home, Icons.Outlined.Home, Icons.Rounded.Home),
        BottomNavItem(R.string.nav_profiles, Icons.Outlined.Tune, Icons.Rounded.Tune),
        BottomNavItem(R.string.nav_diagnostics, Icons.Outlined.Science, Icons.Rounded.Science),
        BottomNavItem(R.string.nav_settings, Icons.Outlined.Settings, Icons.Rounded.Settings),
    )

    val snackbarHostState = remember { SnackbarHostState() }
    val event = state.lastEvent
    val eventMessage = event?.let { eventText(it) }
    LaunchedEffect(event?.timestamp) {
        if (eventMessage != null) {
            snackbarHostState.showSnackbar(eventMessage)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        if (wide) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                    items.forEachIndexed { index, item ->
                        NavigationRailItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = {
                                Icon(
                                    imageVector = if (selectedTab == index) {
                                        item.selectedIcon
                                    } else {
                                        item.icon
                                    },
                                    contentDescription = stringResource(item.labelRes),
                                )
                            },
                            label = { Text(stringResource(item.labelRes)) },
                        )
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    ScreenContent(selectedTab, state, viewModel)
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(16.dp),
                    )
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                ScreenContent(selectedTab, state, viewModel)
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 104.dp),
                )
                FloatingNavBar(
                    items = items,
                    selectedIndex = selectedTab,
                    onSelected = { selectedTab = it },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
private fun ScreenContent(tab: Int, state: UiState, viewModel: MainViewModel) {
    when (tab) {
        0 -> HomeScreen(state, viewModel)
        1 -> ProfilesScreen(state, viewModel)
        2 -> DiagnosticsScreen(state, viewModel)
        else -> SettingsScreen(state, viewModel)
    }
}
