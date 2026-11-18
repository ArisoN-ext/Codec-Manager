package dev.arison.codecmanager

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.arison.codecmanager.data.ThemeMode
import dev.arison.codecmanager.ui.MainScaffold
import dev.arison.codecmanager.ui.MainViewModel
import dev.arison.codecmanager.ui.OnboardingScreen
import dev.arison.codecmanager.ui.theme.CodecManagerTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        splash.setKeepOnScreenCondition { viewModel.state.value.loading }

        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val associationLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartIntentSenderForResult(),
            ) {
                viewModel.onAssociationActivityResult()
            }
            LaunchedEffect(associationLauncher) {
                viewModel.bindAssociationLauncher { intentSender ->
                    associationLauncher.launch(
                        IntentSenderRequest.Builder(intentSender).build(),
                    )
                }
            }
            val dark = when (state.settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            CodecManagerTheme(
                darkTheme = dark,
                dynamicColor = state.settings.dynamicColor && Build.VERSION.SDK_INT >= 31,
                reduceMotion = state.settings.reduceMotion,
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    if (!state.settings.onboardingDone) {
                        OnboardingScreen(state = state, viewModel = viewModel)
                    } else {
                        MainScaffold(state = state, viewModel = viewModel)
                    }
                }
            }
        }
    }
}
