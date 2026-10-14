package com.zendolauncher

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zendolauncher.data.SettingsStore
import com.zendolauncher.data.ThemeMode
import com.zendolauncher.ui.AppListScreen
import com.zendolauncher.ui.HomeScreen
import com.zendolauncher.ui.LauncherViewModel
import com.zendolauncher.ui.OnboardingScreen
import com.zendolauncher.ui.PauseDialog
import com.zendolauncher.ui.Screen
import com.zendolauncher.ui.SettingsScreen
import com.zendolauncher.ui.theme.LauncherColors
import com.zendolauncher.ui.theme.LauncherTheme

/**
 * The home screen. singleTask + stateNotNeeded + clearTaskOnLaunch in the manifest
 * keep it as a stateless root: pressing Home always returns here with no stale task.
 */
class LauncherActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // The window background and the system-bar icon appearance are painted by the
        // window manager before the first Compose frame, so they cannot come from the
        // Compose palette (see SystemBarIcons below, which only reaches the post-Compose
        // window). The manifest theme follows the *system* night mode through values-night,
        // but the theme here is app-controlled, so an explicit choice must be applied to
        // the window before it exists: setTheme() after super.onCreate() is too late.
        // SYSTEM does nothing, keeping the manifest theme and its night-qualified values.
        when (SettingsStore(this).read().theme) {
            ThemeMode.LIGHT -> setTheme(R.style.Theme_ZendoLauncher_Light)
            ThemeMode.DARK -> setTheme(R.style.Theme_ZendoLauncher_Dark)
            ThemeMode.SYSTEM -> Unit
        }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            LauncherTheme(mode = state.settings.theme) {
                SystemBarIcons()
                LauncherRoot(viewModel, state)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onStart()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshHomeState()
    }

    override fun onStop() {
        viewModel.onStop()
        super.onStop()
    }
}

/**
 * Keeps the system-bar icons readable against the launcher's own background.
 *
 * enableEdgeToEdge()'s default SystemBarStyle.auto decides icon colour from the *system*
 * dark setting, but this launcher's theme is app-controlled (state.settings.theme). On a
 * dark system with the Light theme chosen, the default leaves light icons over a
 * near-white background: invisible clock, battery and notifications. Deriving the
 * appearance from the resolved palette instead makes the two agree in every combination.
 * The transparent scrims from enableEdgeToEdge() are left alone; only icon appearance
 * is overridden here.
 */
@Composable
private fun SystemBarIcons() {
    val dark = LauncherColors.current.isDark
    val view = LocalView.current
    SideEffect {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}

@Composable
private fun LauncherRoot(viewModel: LauncherViewModel, state: com.zendolauncher.ui.UiState) {
    // Home is a back-stack root: Back from a secondary screen returns home. Onboarding
    // owns its own BackHandler so Back dismisses it rather than dropping into Home unseen.
    // Back from Settings returns to the drawer when entered from it, keeping the query.
    BackHandler(enabled = state.screen == Screen.SETTINGS) {
        viewModel.backFromSettings()
    }
    BackHandler(enabled = state.screen == Screen.APPS) {
        viewModel.goHome()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(LauncherColors.current.background),
    ) {
        when (state.screen) {
            Screen.ONBOARDING -> OnboardingScreen(viewModel, state)
            Screen.HOME -> HomeScreen(viewModel, state)
            Screen.APPS -> AppListScreen(viewModel, state)
            Screen.SETTINGS -> SettingsScreen(viewModel, state)
        }
        // Launcher-level pre-launch only: the dialog interposes here, over whichever
        // list the tap came from, and Home underneath is untouched.
        state.pendingPause?.let {
            PauseDialog(
                label = it.label,
                onContinue = { viewModel.confirmPause() },
                onCancel = { viewModel.cancelPause() },
            )
        }
    }
}
