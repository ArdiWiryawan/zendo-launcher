package com.zendolauncher.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.runtime.LaunchedEffect
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
import com.zendolauncher.core.UsageFormat
import com.zendolauncher.data.LauncherSettings
import com.zendolauncher.data.LayoutMode
import com.zendolauncher.data.SettingsStore
import com.zendolauncher.data.ThemeMode
import com.zendolauncher.ui.theme.LauncherColors
import com.zendolauncher.ui.theme.LauncherSpace
import com.zendolauncher.ui.theme.LauncherType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Settings is a page of text, not a dashboard: sections are grouped by what the user is
 * trying to change — Apps, Calendar, Zendo, History, Screen Time, Wallpaper, Appearance —
 * each control is one row, and nothing is wrapped in a card. Rows that sap attention are
 * absent rather than inert: an off feature tells you how to turn it on, not show controls
 * that do nothing.
 */
@Composable
fun SettingsScreen(viewModel: LauncherViewModel, state: UiState) {
    val palette = LauncherColors.current
    val context = LocalContext.current

    // Hidden apps are stored as launch keys. The label comes from the same discovery the
    // app list uses, so this list reads as app names instead of package ids; a key that no
    // longer resolves is an uninstalled app and falls back to its package name.
    val labels = remember(state.allApps) { state.allApps.associate { it.key to it.label } }

    // Read-only re-check: granting or revoking usage access in Settings while away
    // updates this screen on return. It never requests anything.
    LaunchedEffect(Unit) { viewModel.refreshUsageAccess() }

    // The two file pickers this screen owns. Both read a file the user chose, once:
    // nothing here is a connection, a login or a background sync.
    val snapshotLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.beginSnapshotImport(it) } }
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.beginBackupImport(it) } }

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
            Spacer(Modifier.height(LauncherSpace.md))
            ToggleRow(
                label = "Grayscale icons",
                checked = state.settings.monochromeIcons,
                onCheckedChange = { viewModel.setMonochromeIcons(it) },
            )
            Spacer(Modifier.height(LauncherSpace.xs))
            SecondaryText(
                "Icons are drawn desaturated by default, so app colours never fight the " +
                    "page. Turn off to see each app's own icon.",
            )
            Spacer(Modifier.height(LauncherSpace.md))
            ToggleRow(
                label = "Reduce launcher transitions",
                checked = state.settings.reduceMotion,
                onCheckedChange = { viewModel.setReduceMotion(it) },
            )
            Spacer(Modifier.height(LauncherSpace.xs))
            SecondaryText("Programmatic page jumps stop animating. Swipes still follow your finger.")

            // ---- Apps ----------------------------------------------------------------

            Spacer(Modifier.height(LauncherSpace.xl))
            SectionHeading("Apps")
            Spacer(Modifier.height(LauncherSpace.sm))
            ToggleRow(
                label = "Show today",
                checked = state.settings.todayEnabled,
                onCheckedChange = { viewModel.setTodayEnabled(it) },
            )
            Spacer(Modifier.height(LauncherSpace.xs))
            SecondaryText(
                "Today shows your action, your highlight and your tasks. Highlights stay " +
                    "until you archive them; tasks keep their state.",
            )
            Spacer(Modifier.height(LauncherSpace.md))
            ChoiceRow(
                label = "Pinned apps per page",
                options = listOf(
                    SettingsStore.MIN_PIN_LIMIT to SettingsStore.MIN_PIN_LIMIT.toString(),
                    SettingsStore.MAX_PIN_LIMIT to SettingsStore.MAX_PIN_LIMIT.toString(),
                ),
                selected = state.settings.pinLimit,
                onSelect = { viewModel.setPinLimit(it) },
            )
            Spacer(Modifier.height(LauncherSpace.xs))
            SecondaryText(
                "The cap for one page. When a page is full, pinning more says to remove " +
                    "a pin first instead of quietly doing nothing.",
            )
            Spacer(Modifier.height(LauncherSpace.md))
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
            Spacer(Modifier.height(LauncherSpace.md))
            if (state.settings.hidden.isEmpty()) {
                EmptyNote("No hidden apps.")
            } else {
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
            Spacer(Modifier.height(LauncherSpace.md))
            ToggleRow(
                label = "Horizontal swipe pages Home",
                checked = state.settings.pagingEnabled,
                onCheckedChange = { viewModel.setPagingEnabled(it) },
            )
            Spacer(Modifier.height(LauncherSpace.xs))
            SecondaryText("A deliberate sideways swipe moves to the next Home page. Off keeps pages tappable via dots.")
            Spacer(Modifier.height(LauncherSpace.sm))
            ToggleRow(
                label = "Swipe up on bottom bar opens drawer",
                checked = state.settings.swipeUpEnabled,
                onCheckedChange = { viewModel.setSwipeUpEnabled(it) },
            )
            Spacer(Modifier.height(LauncherSpace.xs))
            SecondaryText("Scrollable content keeps its gesture; only the bottom bar owns the swipe. Buttons always work.")

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
            QuietAction("Open calendar") { viewModel.openCalendar() }
            Spacer(Modifier.height(LauncherSpace.xs))
            SecondaryText(
                "Calendars are chosen on the Calendar screen, one by one. Nothing is " +
                    "auto-enabled, and nothing is ever written back.",
            )

            // ---- Zendo ---------------------------------------------------------------

            Spacer(Modifier.height(LauncherSpace.xl))
            SectionHeading("Zendo")
            Spacer(Modifier.height(LauncherSpace.sm))
            SecondaryText(
                "Zendo and this launcher share no account and no network. You carry a " +
                    "snapshot file over by hand: the Zendo app exports it, you pick it " +
                    "here, and the preview shows exactly what would change.",
            )
            Spacer(Modifier.height(LauncherSpace.xs))
            SecondaryText(
                "One-way, manual, and the file stays yours. There is no two-way sync " +
                    "and no background connection.",
            )
            QuietAction("Import a Zendo snapshot") {
                snapshotLauncher.launch(
                    arrayOf("application/json", "application/octet-stream", "text/plain"),
                )
            }
            Spacer(Modifier.height(LauncherSpace.xs))
            if (state.settings.zendoLastImportAt > 0L) {
                SecondaryText(
                    "Last import: " +
                        Instant.ofEpochMilli(state.settings.zendoLastImportAt)
                            .atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("d MMM, HH:mm")) +
                        state.settings.zendoLastImportDate?.let { " · snapshot for $it" }.orEmpty(),
                )
            } else {
                SecondaryText("No snapshot imported yet.")
            }
            state.zendoMessage?.let { message ->
                Spacer(Modifier.height(LauncherSpace.xs))
                SecondaryText(message)
                QuietAction("Dismiss", onClick = { viewModel.clearZendoMessage() })
            }

            // ---- History -------------------------------------------------------------

            Spacer(Modifier.height(LauncherSpace.xl))
            SectionHeading("History")
            Spacer(Modifier.height(LauncherSpace.sm))
            SecondaryText(
                "One local record per day: the action, the highlight and the tasks as they " +
                    "stood. Today keeps changing; older days never do.",
            )
            QuietAction("Open history") { viewModel.openHistory() }
            QuietAction("Restore from backup") {
                backupLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
            }
            QuietAction("Clear history") { viewModel.requestClearHistory() }
            Spacer(Modifier.height(LauncherSpace.xs))
            SecondaryText(
                "A backup holds everything local — action, highlights, tasks, day records " +
                    "and behaviour settings. No package names, no calendar ids, no accounts.",
            )

            // ---- Screen Time ---------------------------------------------------------

            Spacer(Modifier.height(LauncherSpace.xl))
            SectionHeading("Screen Time")
            Spacer(Modifier.height(LauncherSpace.sm))
            ToggleRow(
                label = "Screen time summary",
                checked = state.settings.screenTimeEnabled,
                onCheckedChange = { on ->
                    viewModel.setScreenTimeEnabled(on)
                    // Granting happens in system Settings; the toggle is the only
                    // moment this screen sends the user there unprompted, and it
                    // never asks twice on its own.
                    if (on && !state.usageAccess) {
                        openSystemSettings(context, Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    }
                },
            )
            Spacer(Modifier.height(LauncherSpace.xs))
            SecondaryText(
                "Off by default and hidden from Home either way. When on, it shows today's " +
                    "foreground minutes per app — numbers read on this device, never " +
                    "uploaded, never blocking anything. Turn it off and the queries stop.",
            )
            if (state.settings.screenTimeEnabled) {
                Spacer(Modifier.height(LauncherSpace.sm))
                if (!state.usageAccess) {
                    SecondaryText(
                        "Access is not granted yet. Android grants Screen Time access in " +
                            "system Settings, under Special app access → Usage access.",
                    )
                    QuietAction("Open screen time settings") {
                        openSystemSettings(context, Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    }
                    QuietAction("Check access") { viewModel.refreshUsageAccess() }
                } else {
                    ToggleRow(
                        label = "Hide summary",
                        checked = state.settings.screenTimeHidden,
                        onCheckedChange = { viewModel.setScreenTimeHidden(it) },
                    )
                    Spacer(Modifier.height(LauncherSpace.xs))
                    SecondaryText(
                        if (state.settings.screenTimeHidden) {
                            "Summary hidden. Access stays until you revoke it in system Settings."
                        } else {
                            "Today's foreground minutes per app. Refreshed only when you ask."
                        },
                    )
                    if (!state.settings.screenTimeHidden) {
                        val pkgLabels = remember(state.allApps) {
                            state.allApps.associate { it.packageName to it.label }
                        }
                        Spacer(Modifier.height(LauncherSpace.sm))
                        if (state.usageLoading) {
                            SecondaryText("Loading…")
                        } else {
                            val entries = state.usageEntries.take(8)
                            if (entries.isEmpty()) {
                                EmptyNote("No usage recorded today. Tap Refresh after using some apps.")
                            } else {
                                entries.forEachIndexed { index, entry ->
                                    if (index > 0) Hairline()
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            pkgLabels[entry.packageName] ?: entry.packageName,
                                            style = LauncherType.body,
                                            color = palette.primary,
                                            modifier = Modifier.weight(1f),
                                        )
                                        SecondaryText(UsageFormat.minutes(entry.minutes))
                                    }
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(LauncherSpace.lg)) {
                            QuietAction("Refresh", onClick = { viewModel.loadUsage() })
                            QuietAction("Check access") { viewModel.refreshUsageAccess() }
                        }
                    }
                }
            }

            // ---- Wallpaper -----------------------------------------------------------

            Spacer(Modifier.height(LauncherSpace.xl))
            SectionHeading("Wallpaper")
            Spacer(Modifier.height(LauncherSpace.sm))
            SecondaryText(
                "Three quiet grayscale templates, made for this screen. Preview first — " +
                    "nothing is applied until the system reports success.",
            )
            QuietAction("Open wallpaper gallery") { viewModel.openWallpapers() }
            Spacer(Modifier.height(LauncherSpace.xs))
            SecondaryText(
                "Lock and Home targets appear only where the device supports them. " +
                    "This sets a background; it never draws over your lock screen.",
            )

            // ---- Setup, permissions and system ---------------------------------------

            Spacer(Modifier.height(LauncherSpace.xl))
            SectionHeading("Setup & permissions")
            Spacer(Modifier.height(LauncherSpace.sm))
            SecondaryText("First-run checklist and one place for every capability.")
            QuietAction("Review setup") { viewModel.openSetup(Screen.SETTINGS) }
            QuietAction("Permission center") { viewModel.openPermissions(Screen.SETTINGS) }

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
            QuietAction("Wallpaper (system picker)") {
                openSystemSettings(context, Intent.ACTION_SET_WALLPAPER)
            }

            Spacer(Modifier.height(LauncherSpace.xl))
            SectionHeading("Privacy")
            Spacer(Modifier.height(LauncherSpace.sm))
            SecondaryText(
                "Everything stays on this device. Your app list, pinned apps, hidden apps, theme, " +
                    "highlights, tasks, history and wallpaper choice are stored locally and are never uploaded. " +
                    "The launcher does not use the internet. App discovery uses the system's LauncherApps service. " +
                    "Calendar is read-only from your device provider, only after you allow it, and only for the calendars you choose. " +
                    "Screen time is off by default; when on it reads numbers on this device only. " +
                    "Wallpaper apply uses the system's WallpaperManager for the bundled grayscale templates.",
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

/**
 * The access window as one compact block: the switch, the two ends, and the sentence
 * that keeps the feature honest.
 *
 * It sits inside Intentional Pause rather than getting its own heading, because it has
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
            "that is already open — and this gates launches from Zendo Launcher only. It " +
            "does not block apps system-wide, and opening the same app from another " +
            "launcher or from a notification is untouched.",
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

/**
 * Opens a system Settings page from inside the launcher. The primary action is tried
 * first; when the device has no activity for it, the fallback page is tried, and a
 * failure of both is stated instead of leaving a tap that did nothing.
 */
private fun openSystemSettings(context: Context, primary: String, fallback: String? = null) {
    try {
        context.startActivity(Intent(primary).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        val recovered = fallback?.let {
            try {
                context.startActivity(Intent(it).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            } catch (_: ActivityNotFoundException) {
                false
            }
        } ?: false
        if (!recovered) {
            Toast.makeText(context, "This device has no page for that setting.", Toast.LENGTH_SHORT).show()
        }
    }
}
