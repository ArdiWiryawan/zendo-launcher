package com.zendolauncher.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import com.zendolauncher.BuildConfig
import com.zendolauncher.data.LauncherSettings
import com.zendolauncher.data.SettingsStore
import com.zendolauncher.data.ThemeMode
import com.zendolauncher.ui.theme.LauncherColors
import com.zendolauncher.ui.theme.LauncherSpace
import com.zendolauncher.ui.theme.LauncherType

/**
 * Settings is a short page of text, not a dashboard: five sections, each one a
 * question the user actually asks — what it looks like, what it opens, what it keeps,
 * what it pauses, what it is.
 *
 * Everything that used to live here and did not earn its row is gone: pin limits and
 * pinned-app order (the drawer and the page itself own those), 24-hour clock, icon style,
 * tint, match-wallpaper, layout mode, the wallpaper gallery, screen-time summaries, the
 * Zendo snapshot import, gesture toggles, and the system-settings shortcuts that only
 * forwarded to Android's own pages. Each was a setting for something a person either
 * never changes or changes where they already are.
 */
@Composable
fun SettingsScreen(viewModel: LauncherViewModel, state: UiState) {
    val palette = LauncherColors.current
    val context = LocalContext.current

    // Hidden apps are stored as launch keys. The label comes from the same discovery the
    // app list uses, so this list reads as app names instead of package ids; a key that no
    // longer resolves is an uninstalled app and falls back to its package name.
    val labels = remember(state.allApps) { state.allApps.associate { it.key to it.label } }

    // The one file picker this screen owns: it reads a backup the user chose, once.
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.beginBackupImport(it) } }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let { viewModel.exportBackupTo(it) } }

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

            // ---- Appearance ---------------------------------------------------------

            Spacer(Modifier.height(LauncherSpace.lg))
            Hairline()
            Spacer(Modifier.height(LauncherSpace.md))
            SectionHeading("Appearance")
            Spacer(Modifier.height(LauncherSpace.sm))
            ChoiceRow(
                label = "Theme",
                options = listOf(
                    ThemeMode.LIGHT to "Light",
                    ThemeMode.DARK to "Dark",
                ),
                selected = state.settings.theme,
                onSelect = { viewModel.setTheme(it) },
            )
            Spacer(Modifier.height(LauncherSpace.md))
            ToggleRow(
                label = "Reduce transitions",
                checked = state.settings.reduceMotion,
                onCheckedChange = { viewModel.setReduceMotion(it) },
            )
            Spacer(Modifier.height(LauncherSpace.xs))
            SecondaryText("Page jumps stop animating. Swipes still follow your finger.")

            // ---- Apps ----------------------------------------------------------------

            Spacer(Modifier.height(LauncherSpace.xl))
            SectionHeading("Apps")
            Spacer(Modifier.height(LauncherSpace.sm))
            SecondaryText(
                "Open All apps to add an app to Home, reorder the list, or hide one. " +
                    "The list is the page — there is nothing to arrange here.",
            )
            Spacer(Modifier.height(LauncherSpace.sm))
            QuietAction("Open All apps", onClick = { viewModel.openApps() })
            if (state.settings.hidden.isNotEmpty()) {
                Spacer(Modifier.height(LauncherSpace.md))
                SecondaryText("Hidden apps")
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

            // ---- Calendar ------------------------------------------------------------

            Spacer(Modifier.height(LauncherSpace.xl))
            SectionHeading("Calendar")
            Spacer(Modifier.height(LauncherSpace.sm))
            SecondaryText(
                if (!state.calendarPermission) {
                    "Calendar access is off. The launcher reads your device calendar only, " +
                        "offline, and only after you allow it there."
                } else if (state.settings.shownCalendarIds.isEmpty()) {
                    "Access is granted, but no calendars are chosen, so the agenda reads nothing."
                } else {
                    "Reading ${state.settings.shownCalendarIds.size} of ${state.calendars.size} calendars, " +
                        "from your device provider only."
                },
            )
            Spacer(Modifier.height(LauncherSpace.sm))
            if (!state.calendarPermission) {
                CalendarAccessAction(viewModel, state.settings.calendarAsked)
            }
            QuietAction("Open calendar") { viewModel.openCalendar() }

            // ---- Data ----------------------------------------------------------------

            Spacer(Modifier.height(LauncherSpace.xl))
            SectionHeading("Data")
            Spacer(Modifier.height(LauncherSpace.sm))
            SecondaryText(
                "Everything stays on this device. A backup is one file you choose, holding " +
                    "your action, highlights, tasks and day records — no package names, no " +
                    "calendar ids, no accounts.",
            )
            Spacer(Modifier.height(LauncherSpace.sm))
            QuietAction("Save a backup") { exportLauncher.launch("zendo-launcher-backup.json") }
            QuietAction("Restore from backup") {
                backupLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
            }
            QuietAction("Open history") { viewModel.openHistory() }
            QuietAction("Clear history") { viewModel.requestClearHistory() }
            state.dataMessage?.let { message ->
                Spacer(Modifier.height(LauncherSpace.xs))
                SecondaryText(message)
                QuietAction("Dismiss", onClick = { viewModel.clearDataMessage() })
            }

            // ---- Pause ---------------------------------------------------------------

            Spacer(Modifier.height(LauncherSpace.xl))
            SectionHeading("Pause")
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
            Spacer(Modifier.height(LauncherSpace.sm))
            PauseMessageField(viewModel, state.settings.pauseMessage)
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
            Spacer(Modifier.height(LauncherSpace.md))
            AccessWindowSection(viewModel, state.settings)

            // ---- Home app ------------------------------------------------------------

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
            Spacer(Modifier.height(LauncherSpace.sm))
            QuietAction("Open Home app settings") {
                openSystemSettings(context, Settings.ACTION_HOME_SETTINGS)
            }

            // ---- About ---------------------------------------------------------------

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
 * The access window as one compact block: the switch, the two ends, and the sentence
 * that keeps the feature honest.
 *
 * It sits inside Pause rather than getting its own heading, because it has
 * no effect on its own — it narrows a gate the section above already turns on. A window
 * with the pause off is a setting that does nothing, so the copy opens with that
 * condition instead of letting the user discover it by tapping an app.
 *
 * The ends are stepped in 30-minute increments. Entry is by tapping rather than typing:
 * a text field would need a keyboard, parsing and a validation story to accept a range
 * that [AccessWindow] already understands exactly.
 */
@Composable
private fun AccessWindowSection(viewModel: LauncherViewModel, settings: LauncherSettings) {
    Spacer(Modifier.height(LauncherSpace.md))
    ToggleRow(
        label = "Limit to a time window",
        checked = settings.windowEnabled,
        onCheckedChange = { viewModel.setWindowEnabled(it) },
    )
    Spacer(Modifier.height(LauncherSpace.xs))
    SecondaryText(
        "While this is on, a checked app opens straight through during the window below " +
            "and takes the pause for the rest of the day — so the window is the stretch " +
            "in which the pause is skipped. A window that ends before it starts is read " +
            "as crossing midnight, so 22:00 to 02:00 works as written.",
    )
    Spacer(Modifier.height(LauncherSpace.sm))
    StepperRow(
        label = "Opens at",
        valueText = AccessWindow.format(settings.windowStartMinute),
        decreaseLabel = "Earlier",
        increaseLabel = "Later",
        onDecrease = { viewModel.setWindowStart(settings.windowStartMinute - WINDOW_STEP_MINUTES) },
        onIncrease = { viewModel.setWindowStart(settings.windowStartMinute + WINDOW_STEP_MINUTES) },
    )
    StepperRow(
        label = "Closes at",
        valueText = AccessWindow.format(settings.windowEndMinute),
        decreaseLabel = "Earlier",
        increaseLabel = "Later",
        onDecrease = { viewModel.setWindowEnd(settings.windowEndMinute - WINDOW_STEP_MINUTES) },
        onIncrease = { viewModel.setWindowEnd(settings.windowEndMinute + WINDOW_STEP_MINUTES) },
    )
    Spacer(Modifier.height(LauncherSpace.xs))
    // Said plainly, because the alternative is a user who believes they have locked
    // themselves out system-wide and cannot work out why nothing else changed.
    SecondaryText(
        "The time is read once, when you tap an app here. Nothing runs in the background " +
            "and nothing is scheduled, so a window that has passed does not close an app " +
            "that is already open — and this gates launches from Zendo Launcher only.",
    )
}

/**
 * The window ends step in half-hours. Tapping in 30-minute steps lands on every
 * clock reading a person would say out loud, without a picker.
 */
private const val WINDOW_STEP_MINUTES = 30

/**
 * The user's own pause reminder, one line. Commits on Done; blank resets to
 * the default line rather than storing an empty dialog.
 */
@Composable
private fun PauseMessageField(viewModel: LauncherViewModel, current: String) {
    val palette = LauncherColors.current
    var draft by remember(current) { mutableStateOf(current) }
    SecondaryText("Reminder note")
    BasicTextField(
        value = draft,
        onValueChange = { draft = it },
        singleLine = true,
        textStyle = LauncherType.body.copy(color = palette.primary),
        cursorBrush = SolidColor(palette.primary),
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { viewModel.setPauseMessage(draft) }),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Pause reminder note" },
        decorationBox = { inner ->
            if (draft.isEmpty()) SecondaryText("Take a breath. Open it anyway?")
            inner()
        },
    )
    Spacer(Modifier.height(LauncherSpace.xs))
    Row(horizontalArrangement = Arrangement.spacedBy(LauncherSpace.lg)) {
        QuietAction("Save note", onClick = { viewModel.setPauseMessage(draft) })
        QuietAction("Reset", onClick = {
            draft = SettingsStore.DEFAULT_PAUSE_MESSAGE
            viewModel.setPauseMessage("")
        })
    }
}

private fun openSystemSettings(context: Context, action: String) {
    runCatching {
        context.startActivity(
            Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
