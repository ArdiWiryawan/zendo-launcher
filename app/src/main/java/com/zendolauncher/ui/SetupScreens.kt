package com.zendolauncher.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.zendolauncher.ui.theme.LauncherColors
import com.zendolauncher.ui.theme.LauncherSpace
import com.zendolauncher.ui.theme.LauncherType

/**
 * First-run setup checklist and the permission center. Every step is
 * optional; statuses reflect live OS state (re-checked on entry), and nothing
 * here requests anything except through the same explicit actions used
 * everywhere else.
 */

// ---- Setup ---------------------------------------------------------------------

@Composable
fun SetupScreen(viewModel: LauncherViewModel, state: UiState) {
    val palette = LauncherColors.current
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.refreshHomeState()
        viewModel.refreshCalendarPermission()
        viewModel.refreshUsageAccess()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.background)
            .systemBarsPadding(),
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LauncherSpace.gutter),
        ) {
            Spacer(Modifier.height(LauncherSpace.lg))
            ScreenTitle("Setup") {}
            Spacer(Modifier.height(LauncherSpace.xs))
            SecondaryText("Only what you need. Skip anything — it stays available in Settings.")
            Spacer(Modifier.height(LauncherSpace.md))

            StepRow(
                number = "1",
                title = "Default Home",
                status = if (state.isDefaultHome) "This launcher is your Home app."
                else "Not the default Home app yet.",
                actionLabel = "Open Home settings",
                onAction = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
            )
            StepRow(
                number = "2",
                title = "Calendar (optional)",
                status = if (state.calendarPermission) "Connected."
                else "Skipped until you allow it.",
                action = { CalendarAccessAction(viewModel, state.settings.calendarAsked) },
            )
            StepRow(
                number = "3",
                title = "Essential apps",
                status = if (state.settings.home.pages.flatMap { it.config.itemKeys }.isEmpty()) {
                    "None pinned yet."
                } else {
                    "${state.settings.home.pages.flatMap { it.config.itemKeys }.size} pinned."
                },
                actionLabel = "Choose in All apps",
                onAction = { viewModel.openApps() },
            )
            StepRow(
                number = "4",
                title = "Focus defaults",
                status = "Work ${state.settings.focusWorkMinutes} min · Break ${
                    if (state.settings.focusBreakMinutes == 0) "off"
                    else "${state.settings.focusBreakMinutes} min"
                }.",
                actionLabel = "Change in Settings",
                onAction = { viewModel.openSettings(Screen.SETUP) },
            )
            StepRow(
                number = "5",
                title = "Wallpaper (optional)",
                status = "Twelve quiet templates to preview.",
                actionLabel = "Open gallery",
                onAction = { viewModel.openWallpapers() },
            )
            StepRow(
                number = "6",
                title = "Pause (optional)",
                status = if (state.settings.pauseEnabled) "Pause is on." else "Off.",
                actionLabel = "Configure",
                onAction = { viewModel.openSettings(Screen.SETUP) },
            )
            Spacer(Modifier.height(LauncherSpace.xl))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = LauncherSpace.gutter, vertical = LauncherSpace.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuietAction("Done", onClick = { viewModel.finishSetup() })
            QuietAction("Skip for now", onClick = { viewModel.finishSetup() })
        }
    }
}

@Composable
private fun StepRow(
    number: String,
    title: String,
    status: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    action: @Composable (() -> Unit)? = null,
) {
    val palette = LauncherColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(number, style = LauncherType.secondary, color = palette.secondary)
        Column(Modifier.weight(1f).padding(start = LauncherSpace.md)) {
            Text(title, style = LauncherType.body, color = palette.primary)
            SecondaryText(status)
            if (action != null) {
                action()
            } else if (actionLabel != null && onAction != null) {
                QuietAction(actionLabel, onClick = onAction)
            }
        }
    }
    Spacer(Modifier.height(LauncherSpace.md))
    Hairline()
    Spacer(Modifier.height(LauncherSpace.md))
}

// ---- Permission center ------------------------------------------------------------

@Composable
fun PermissionsScreen(viewModel: LauncherViewModel, state: UiState) {
    val palette = LauncherColors.current
    val context = LocalContext.current
    // Lifecycle-aware truth: re-read every entry, never a cached claim.
    LaunchedEffect(Unit) {
        viewModel.refreshHomeState()
        viewModel.refreshCalendarPermission()
        viewModel.refreshUsageAccess()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.background)
            .systemBarsPadding(),
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LauncherSpace.gutter),
        ) {
            Spacer(Modifier.height(LauncherSpace.lg))
            ScreenTitle("Permissions") { QuietAction("Home", onClick = { viewModel.goHome() }) }
            Spacer(Modifier.height(LauncherSpace.xs))
            SecondaryText("What is active, what is optional, and how to change either.")
            Spacer(Modifier.height(LauncherSpace.md))

            PermissionRow(
                title = "Calendar",
                state = if (state.calendarPermission) "Allowed" else "Off",
                note = "Reads your agenda for Upcoming. Highlights, tasks, and the focus " +
                    "timer work fully without it.",
                action = { CalendarAccessAction(viewModel, state.settings.calendarAsked) },
            )
            PermissionRow(
                title = "Usage statistics",
                state = if (state.usageAccess) "Allowed" else "Off",
                note = "Optional numbers-only summary in Settings. Revoke anytime on the " +
                    "same system page.",
                actionLabel = if (state.usageAccess) "Manage access" else "Open usage access",
                onAction = {
                    openSystemSettings(
                        context,
                        Settings.ACTION_USAGE_ACCESS_SETTINGS,
                    )
                },
            )
            PermissionRow(
                title = "Wallpaper",
                state = "Always available",
                note = "Direct apply uses the auto-granted system permission; " +
                    "the picker fallback needs none.",
                actionLabel = "Open gallery",
                onAction = { viewModel.openWallpapers() },
            )
            PermissionRow(
                title = "Default Home",
                state = if (state.isDefaultHome) "This launcher" else "Not this launcher",
                note = "Android owns this choice. Web shortcuts only appear while default.",
                actionLabel = "Open Home settings",
                onAction = {
                    openSystemSettings(context, Settings.ACTION_HOME_SETTINGS)
                },
            )
            Spacer(Modifier.height(LauncherSpace.xl))
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    state: String,
    note: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    action: @Composable (() -> Unit)? = null,
) {
    val palette = LauncherColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = LauncherType.body, color = palette.primary, modifier = Modifier.weight(1f))
        SecondaryText(state)
    }
    SecondaryText(note)
    if (action != null) {
        action()
    } else if (actionLabel != null && onAction != null) {
        QuietAction(actionLabel, onClick = onAction)
    }
    Spacer(Modifier.height(LauncherSpace.md))
    Hairline()
    Spacer(Modifier.height(LauncherSpace.md))
}

private fun openSystemSettings(context: android.content.Context, action: String) {
    runCatching {
        context.startActivity(
            android.content.Intent(action).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
