package com.zendolauncher.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zendolauncher.data.LayoutMode
import com.zendolauncher.data.ThemeMode
import com.zendolauncher.ui.theme.LauncherColors
import com.zendolauncher.ui.theme.LauncherType

@Composable
fun SettingsScreen(viewModel: LauncherViewModel, state: UiState) {
    val palette = LauncherColors.current
    val context = LocalContext.current

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
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Settings", style = LauncherType.appLabel, color = palette.primary)
                Spacer(Modifier.weight(1f))
                QuietAction("Home", onClick = { viewModel.goHome() })
            }

            Spacer(Modifier.height(24.dp))
            Hairline()
            Spacer(Modifier.height(20.dp))

            SectionHeading("Appearance")
            Spacer(Modifier.height(10.dp))
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
            Spacer(Modifier.height(14.dp))
            ChoiceRow(
                label = "Layout",
                options = listOf(
                    LayoutMode.TEXT to "Text",
                    LayoutMode.ICON_TEXT to "Icon + text",
                ),
                selected = state.settings.layout,
                onSelect = { viewModel.setLayout(it) },
            )
            Spacer(Modifier.height(14.dp))
            ToggleRow(
                label = "24-hour clock",
                checked = state.settings.clock24h,
                onCheckedChange = { viewModel.setClock24h(it) },
            )

            Spacer(Modifier.height(28.dp))
            SectionHeading("Today section")
            Spacer(Modifier.height(10.dp))
            ToggleRow(
                label = "Show today",
                checked = state.settings.todayEnabled,
                onCheckedChange = { viewModel.setTodayEnabled(it) },
            )
            Spacer(Modifier.height(6.dp))
            EmptyNote(
                "Turn this off for a fully minimal home screen: clock, date and your pinned apps only.",
            )

            Spacer(Modifier.height(28.dp))
            SectionHeading("Zendo")
            Spacer(Modifier.height(10.dp))
            ToggleRow(
                label = "Connect Zendo",
                checked = state.settings.zendoEnabled,
                onCheckedChange = { viewModel.setZendoEnabled(it) },
            )
            Spacer(Modifier.height(6.dp))
            EmptyNote(
                "Zendo currently exposes no planning API, so connecting does not yet fetch anything. " +
                    "The launcher works fully without it. See docs/zendo-integration.md.",
            )

            Spacer(Modifier.height(28.dp))
            SectionHeading("Home screen")
            Spacer(Modifier.height(10.dp))
            SecondaryText(
                if (state.isDefaultHome) {
                    "This launcher is your default Home app."
                } else {
                    "This launcher is not the default Home app yet."
                },
            )
            Spacer(Modifier.height(6.dp))
            QuietAction("Open Home app settings") {
                val intent = Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
            }
            Spacer(Modifier.height(8.dp))
            EmptyNote(
                if (state.shortcutAccess) {
                    "Pinned shortcuts from other apps are readable, so installed web apps appear in your app list."
                } else {
                    "Pinned shortcuts are only readable while this launcher is the default Home app. " +
                        "Set it as default to see web apps that browsers expose as shortcuts."
                },
            )

            Spacer(Modifier.height(28.dp))
            SectionHeading("Pinned order")
            Spacer(Modifier.height(10.dp))
            if (state.favourites.isEmpty()) {
                EmptyNote("No pinned apps.")
            } else {
                state.favourites.forEachIndexed { index, target ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(target.label, style = LauncherType.body, color = palette.primary, modifier = Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            QuietAction("Up") { viewModel.moveFavourite(target.key, -1) }
                            QuietAction("Down") { viewModel.moveFavourite(target.key, 1) }
                            QuietAction("Unpin") { viewModel.toggleFavourite(target.key) }
                        }
                    }
                    Hairline()
                }
            }

            Spacer(Modifier.height(28.dp))
            SectionHeading("Hidden apps")
            Spacer(Modifier.height(10.dp))
            if (state.settings.hidden.isEmpty()) {
                EmptyNote("No hidden apps.")
            } else {
                state.settings.hidden.sorted().forEach { key ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            key.substringBefore('/'),
                            style = LauncherType.body,
                            color = palette.primary,
                            modifier = Modifier.weight(1f),
                        )
                        QuietAction("Unhide") { viewModel.toggleHidden(key) }
                    }
                    Hairline()
                }
            }

            Spacer(Modifier.height(28.dp))
            SectionHeading("Privacy")
            Spacer(Modifier.height(10.dp))
            SecondaryText(
                "Everything stays on this device. Your app list, pinned apps, hidden apps and theme are stored " +
                    "locally and are never uploaded. The launcher requests no runtime permissions and does not " +
                    "use the internet. App discovery uses the system's LauncherApps service.",
            )

            Spacer(Modifier.height(24.dp))
            SectionHeading("About")
            Spacer(Modifier.height(10.dp))
            SecondaryText("Zendo Launcher 0.1.0 — a standalone, monochrome home screen.")
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val palette = LauncherColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = LauncherType.body, color = palette.primary, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = palette.background,
                checkedTrackColor = palette.primary,
                checkedBorderColor = palette.primary,
                uncheckedThumbColor = palette.secondary,
                uncheckedTrackColor = palette.background,
                uncheckedBorderColor = palette.separator,
            ),
        )
    }
}

@Composable
private fun <T> ChoiceRow(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    val palette = LauncherColors.current
    Column {
        Text(label, style = LauncherType.body, color = palette.primary)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            options.forEach { (value, text) ->
                Text(
                    text = text,
                    style = LauncherType.action,
                    color = if (value == selected) palette.primary else palette.secondary,
                    modifier = Modifier
                        .clickable { onSelect(value) }
                        .padding(vertical = 6.dp),
                )
            }
        }
    }
}
