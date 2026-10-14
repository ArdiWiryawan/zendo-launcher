package com.zendolauncher.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.zendolauncher.BuildConfig
import com.zendolauncher.data.LayoutMode
import com.zendolauncher.data.ThemeMode
import com.zendolauncher.ui.theme.LauncherColors
import com.zendolauncher.ui.theme.LauncherSpace
import com.zendolauncher.ui.theme.LauncherType

/**
 * Settings is a page of text, not a dashboard: sections are grouped by what the user is
 * trying to change, each control is one row, and nothing is wrapped in a card. The two
 * things a user cannot discover elsewhere (which Home app they are running, and whether
 * their pinned shortcuts are readable) are stated in words rather than implied by a
 * control.
 */
@Composable
fun SettingsScreen(viewModel: LauncherViewModel, state: UiState) {
    val palette = LauncherColors.current
    val context = LocalContext.current

    // Hidden apps are stored as launch keys. The label comes from the same discovery the
    // app list uses, so this list reads as app names instead of package ids; a key that no
    // longer resolves is an uninstalled app and falls back to its package name.
    val labels = remember(state.allApps) { state.allApps.associate { it.key to it.label } }

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
            ScreenTitle("Settings") { QuietAction("Home", onClick = { viewModel.goHome() }) }

            Spacer(Modifier.height(LauncherSpace.lg))
            Hairline()
            Spacer(Modifier.height(LauncherSpace.md))

            SectionHeading("Appearance")
            Spacer(Modifier.height(LauncherSpace.sm))
            ChoiceRow(
                label = "Theme",
                options = listOf(
                    ThemeMode.SYSTEM to "System",
                    ThemeMode.LIGHT to "Light",
                    ThemeMode.DARK to "Dark",
                ),
                selected = state.settings.theme,
                onSelect = { viewModel.setTheme(it) },
            )
            Spacer(Modifier.height(LauncherSpace.md))
            ChoiceRow(
                label = "Layout",
                options = listOf(
                    LayoutMode.TEXT to "Text",
                    LayoutMode.ICON_TEXT to "Icon + text",
                ),
                selected = state.settings.layout,
                onSelect = { viewModel.setLayout(it) },
            )
            Spacer(Modifier.height(LauncherSpace.md))
            ToggleRow(
                label = "24-hour clock",
                checked = state.settings.clock24h,
                onCheckedChange = { viewModel.setClock24h(it) },
            )

            // The Today block and the source that fills it are one intent, so they are one
            // section rather than two headings over a single feature.
            Spacer(Modifier.height(LauncherSpace.xl))
            SectionHeading("Today")
            Spacer(Modifier.height(LauncherSpace.sm))
            ToggleRow(
                label = "Show today",
                checked = state.settings.todayEnabled,
                onCheckedChange = { viewModel.setTodayEnabled(it) },
            )
            Spacer(Modifier.height(LauncherSpace.xs))
            // The toggle that looks broken explains itself here rather than under the
            // other one: turning it on cannot change the screen while nothing fills the block.
            SecondaryText(
                "Home has a place for the Today block, between the date and your pinned apps. " +
                    "Nothing fills it yet, so Home looks the same either way.",
            )
            Spacer(Modifier.height(LauncherSpace.md))
            ToggleRow(
                label = "Connect Zendo",
                checked = state.settings.zendoEnabled,
                onCheckedChange = { viewModel.setZendoEnabled(it) },
            )
            Spacer(Modifier.height(LauncherSpace.xs))
            SecondaryText(
                "Zendo exposes no planning API yet, so connecting it fetches nothing. " +
                    "The launcher works fully without it.",
            )

            Spacer(Modifier.height(LauncherSpace.xl))
            SectionHeading("Home app")
            Spacer(Modifier.height(LauncherSpace.sm))
            SecondaryText(
                if (state.isDefaultHome) {
                    "This launcher is your default Home app."
                } else {
                    "This launcher is not the default Home app yet."
                },
            )
            QuietAction("Open Home app settings") {
                openSystemSettings(context, Settings.ACTION_HOME_SETTINGS)
            }
            Spacer(Modifier.height(LauncherSpace.xs))
            SecondaryText(
                if (state.shortcutAccess) {
                    "Pinned shortcuts from other apps are readable, so installed web apps appear in your app list."
                } else {
                    "Pinned shortcuts are only readable while this launcher is the default Home app. " +
                        "Set it as default to see web apps that browsers expose as shortcuts."
                },
            )

            Spacer(Modifier.height(LauncherSpace.xl))
            SectionHeading("Essentials")
            Spacer(Modifier.height(LauncherSpace.sm))
            if (state.favourites.isEmpty()) {
                EmptyNote("No pinned apps.")
            } else {
                state.favourites.forEachIndexed { index, target ->
                    if (index > 0) Hairline()
                    PinnedOrderRow(
                        label = target.label,
                        onMoveUp = { viewModel.moveFavourite(target.key, -1) },
                        onMoveDown = { viewModel.moveFavourite(target.key, 1) },
                        onUnpin = { viewModel.toggleFavourite(target.key) },
                    )
                }
            }

            Spacer(Modifier.height(LauncherSpace.xl))
            SectionHeading("Hidden apps")
            Spacer(Modifier.height(LauncherSpace.sm))
            if (state.settings.hidden.isEmpty()) {
                EmptyNote("No hidden apps.")
            } else {
                state.settings.hidden.sorted().forEachIndexed { index, key ->
                    if (index > 0) Hairline()
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            labels[key] ?: key.substringBefore('/'),
                            style = LauncherType.body,
                            color = palette.primary,
                            modifier = Modifier.weight(1f),
                        )
                        QuietAction("Unhide") { viewModel.toggleHidden(key) }
                    }
                }
            }

            Spacer(Modifier.height(LauncherSpace.xl))
            SectionHeading("Intentional Pause")
            Spacer(Modifier.height(LauncherSpace.sm))
            ToggleRow(
                label = "Pause before opening",
                checked = state.settings.pauseEnabled,
                onCheckedChange = { viewModel.setPauseEnabled(it) },
            )
            Spacer(Modifier.height(LauncherSpace.xs))
            SecondaryText(
                "When on, opening a checked app below shows a calm pause first. " +
                    "This only interposes on launches from this launcher; it does not " +
                    "block apps system-wide, and web links opened in Chrome are not intercepted.",
            )
            Spacer(Modifier.height(LauncherSpace.md))
            if (state.allApps.isEmpty()) {
                EmptyNote("No apps listed yet.")
            } else {
                state.allApps.sortedBy { it.label.lowercase() }.forEachIndexed { index, target ->
                    if (index > 0) Hairline()
                    ToggleRow(
                        label = target.label,
                        checked = target.key in state.settings.distract,
                        onCheckedChange = { viewModel.toggleDistract(target.key) },
                    )
                }
            }

            Spacer(Modifier.height(LauncherSpace.xl))
            SectionHeading("System settings")
            Spacer(Modifier.height(LauncherSpace.sm))
            SecondaryText(
                "Connectivity, display, and notification controls belong to Android. " +
                    "These open the system pages directly rather than duplicating toggles.",
            )
            QuietAction("Internet & Wi-Fi") {
                val primary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Settings.Panel.ACTION_INTERNET_CONNECTIVITY
                } else {
                    Settings.ACTION_WIFI_SETTINGS
                }
                openSystemSettings(context, primary, Settings.ACTION_WIFI_SETTINGS)
            }
            QuietAction("Display & lock screen") {
                openSystemSettings(context, Settings.ACTION_DISPLAY_SETTINGS)
            }
            QuietAction("Sound & notifications") {
                openSystemSettings(context, Settings.ACTION_SOUND_SETTINGS)
            }
            QuietAction("Wallpaper") {
                openSystemSettings(context, Intent.ACTION_SET_WALLPAPER)
            }

            Spacer(Modifier.height(LauncherSpace.xl))
            SectionHeading("Privacy")
            Spacer(Modifier.height(LauncherSpace.sm))
            SecondaryText(
                "Everything stays on this device. Your app list, pinned apps, hidden apps and theme are stored " +
                    "locally and are never uploaded. The launcher requests no runtime permissions and does not " +
                    "use the internet. App discovery uses the system's LauncherApps service.",
            )

            Spacer(Modifier.height(LauncherSpace.xl))
            SectionHeading("About")
            Spacer(Modifier.height(LauncherSpace.sm))
            SecondaryText(
                "Zendo Launcher ${BuildConfig.VERSION_NAME}, a standalone monochrome home screen.",
            )
            Spacer(Modifier.height(LauncherSpace.sm))
            // Onboarding is the only place the app explains what it replaces and how to get
            // back, so it stays reachable after the first run rather than being shown once.
            QuietAction("Reopen onboarding") { viewModel.showOnboarding() }
            Spacer(Modifier.height(LauncherSpace.xl))
        }
    }
}

/**
 * One pinned app with its two kinds of action kept apart. Up and Down sit together as the
 * reorder pair; Unpin is destructive and not reversible from this row, so it takes a
 * deliberate step away from them and the trailing edge of the row. The label keeps
 * `weight(1f)`, so a long app name wraps instead of pushing the actions off-screen.
 *
 * Each action names the app it acts on, because three identical words repeated down the
 * list are otherwise indistinguishable to a screen reader.
 */
@Composable
private fun PinnedOrderRow(
    label: String,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onUnpin: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = LauncherType.body,
            color = LauncherColors.current.primary,
            modifier = Modifier.weight(1f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(LauncherSpace.md)) {
            QuietAction(
                text = "Up",
                onClick = onMoveUp,
                modifier = Modifier.semantics { contentDescription = "Move $label up" },
            )
            QuietAction(
                text = "Down",
                onClick = onMoveDown,
                modifier = Modifier.semantics { contentDescription = "Move $label down" },
            )
        }
        Spacer(Modifier.width(LauncherSpace.xl))
        QuietAction(
            text = "Unpin",
            onClick = onUnpin,
            modifier = Modifier.semantics { contentDescription = "Unpin $label" },
        )
    }
}

private fun openSystemSettings(context: Context, action: String, fallbackAction: String? = null) {
    try {
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        if (fallbackAction != null && fallbackAction != action) {
            try {
                context.startActivity(Intent(fallbackAction).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
            }
        }
        Toast.makeText(context, "Settings page unavailable", Toast.LENGTH_SHORT).show()
    }
}

