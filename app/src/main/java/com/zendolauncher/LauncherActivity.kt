package com.zendolauncher

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zendolauncher.ui.AppListScreen
import com.zendolauncher.ui.HomeScreen
import com.zendolauncher.ui.LauncherViewModel
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
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            LauncherTheme(mode = state.settings.theme) {
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

@Composable
private fun LauncherRoot(viewModel: LauncherViewModel, state: com.zendolauncher.ui.UiState) {
    // Home is a back-stack root: Back from a secondary screen returns home.
    BackHandler(enabled = state.screen != Screen.HOME) { viewModel.goHome() }

    Box(
        Modifier
            .fillMaxSize()
            .background(LauncherColors.current.background),
    ) {
        when (state.screen) {
            Screen.HOME -> HomeScreen(viewModel, state)
            Screen.APPS -> AppListScreen(viewModel, state)
            Screen.SETTINGS -> SettingsScreen(viewModel, state)
        }
    }
}
