package com.zendolauncher.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zendolauncher.BuildConfig
import com.zendolauncher.core.MindfulGate
import com.zendolauncher.core.SocialAppDefaults
import com.zendolauncher.data.HomeAlignment
import com.zendolauncher.data.LabelCase
import com.zendolauncher.data.LauncherSettings
import com.zendolauncher.data.SettingsStore
import com.zendolauncher.data.ThemeMode
import com.zendolauncher.platform.UsageRepository
import com.zendolauncher.ui.theme.BackdropStyle
import com.zendolauncher.ui.theme.HomeBackdrop
import com.zendolauncher.ui.theme.LauncherColors
import com.zendolauncher.ui.theme.LauncherSpace
import com.zendolauncher.ui.theme.LauncherType
import com.zendolauncher.ui.theme.ScrimStrength
import com.zendolauncher.ui.theme.label

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(viewModel: LauncherViewModel, state: UiState) {
    val palette = LauncherColors.current
    val context = LocalContext.current

    val labels = remember(state.allApps) { state.allApps.associate { it.key to it.label } }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.beginBackupImport(it) } }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let { viewModel.exportBackupTo(it) } }
    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.importWallpaperPhoto(it) } }

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

            WallpaperPreviewCard(
                settings = state.settings,
                customBitmap = state.customWallpaperBitmap,
            )
            Spacer(Modifier.height(LauncherSpace.md))

            ChoiceRow(
                label = "Theme",
                options = listOf(
                    ThemeMode.LIGHT to "Warm Sage",
                    ThemeMode.DARK to "Matte Charcoal",
                ),
                selected = state.settings.theme,
                onSelect = { viewModel.setTheme(it) },
            )
            Spacer(Modifier.height(LauncherSpace.md))
            ChoiceRow(
                label = "Atmosphere",
                options = listOf(
                    BackdropStyle.MATTE to BackdropStyle.MATTE.label(),
                    BackdropStyle.WINDOW_SHADOW to BackdropStyle.WINDOW_SHADOW.label(),
                    BackdropStyle.WASHI_LINEN to BackdropStyle.WASHI_LINEN.label(),
                    BackdropStyle.MORNING_MIST to BackdropStyle.MORNING_MIST.label(),
                ),
                selected = state.settings.backdropStyle,
                onSelect = { viewModel.setBackdropStyle(it) },
            )
            Spacer(Modifier.height(LauncherSpace.xs))
            SecondaryText(
                "Layers subtly over matte or your custom photo. Watch the preview above to compare.",
            )
            Spacer(Modifier.height(LauncherSpace.sm))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(LauncherSpace.lg),
                verticalArrangement = Arrangement.spacedBy(LauncherSpace.xs),
            ) {
                QuietAction("Choose photo") { photoPickerLauncher.launch(arrayOf("image/*")) }
                if (state.customWallpaperBitmap != null) {
                    QuietAction("Remove photo") { viewModel.clearWallpaperPhoto() }
                }
            }
            if (state.customWallpaperBitmap != null) {
                Spacer(Modifier.height(LauncherSpace.sm))
                ToggleRow(
                    label = "Use custom photo",
                    checked = state.settings.useCustomPhoto,
                    onCheckedChange = { viewModel.setUseCustomPhoto(it) },
                )
                if (state.settings.useCustomPhoto) {
                    Spacer(Modifier.height(LauncherSpace.sm))
                    ToggleRow(
                        label = "Soft blur on photo",
                        checked = state.settings.backdropBlur,
                        onCheckedChange = { viewModel.setBackdropBlur(it) },
                    )
                }
            }
            Spacer(Modifier.height(LauncherSpace.md))
            ChoiceRow(
                label = "Wallpaper scrim",
                options = listOf(
                    ScrimStrength.SUBTLE to ScrimStrength.SUBTLE.label(),
                    ScrimStrength.BALANCED to ScrimStrength.BALANCED.label(),
                    ScrimStrength.DEEP to ScrimStrength.DEEP.label(),
                ),
                selected = state.settings.scrimStrength,
                onSelect = { viewModel.setScrimStrength(it) },
            )
            Spacer(Modifier.height(LauncherSpace.md))
            ChoiceRow(
                label = "Home alignment",
                options = listOf(
                    HomeAlignment.CENTER to "Center",
                    HomeAlignment.LEFT to "Left",
                ),
                selected = state.settings.homeAlignment,
                onSelect = { viewModel.setHomeAlignment(it) },
            )
            Spacer(Modifier.height(LauncherSpace.md))
            ChoiceRow(
                label = "Home app labels",
                options = listOf(
                    LabelCase.LOWERCASE to "lowercase",
                    LabelCase.ORIGINAL to "Original",
                ),
                selected = state.settings.labelCase,
                onSelect = { viewModel.setLabelCase(it) },
            )
            Spacer(Modifier.height(LauncherSpace.md))
            ToggleRow(
                label = "Reduce transitions",
                checked = state.settings.reduceMotion,
                onCheckedChange = { viewModel.setReduceMotion(it) },
            )
            Spacer(Modifier.height(LauncherSpace.xs))
            SecondaryText("Page jumps stop animating. Swipes still follow your finger.")

            // ---- Glance & Usage ------------------------------------------------------

            Spacer(Modifier.height(LauncherSpace.xl))
            SectionHeading("Glance & Usage")
            Spacer(Modifier.height(LauncherSpace.sm))
            ToggleRow(
                label = "Calendar glance on Home",
                checked = state.settings.showCalendarGlance,
                onCheckedChange = { viewModel.setShowCalendarGlance(it) },
            )
            Spacer(Modifier.height(LauncherSpace.sm))
            ToggleRow(
                label = "Screen usage whispers",
                checked = state.settings.showUsageOnHome,
                onCheckedChange = { viewModel.setShowUsageOnHome(it) },
            )
            Spacer(Modifier.height(LauncherSpace.xs))
            SecondaryText(
                if (state.usageAccess) {
                    "Usage access is on. Today's duration shows quietly beside apps and inside the Mindful Gate."
                } else {
                    "Optional, offline only. On Android 13+, if Usage Access shows \"Restricted setting\", " +
                        "open App Info → top-right ⋮ menu → Allow restricted settings first."
                },
            )
            Spacer(Modifier.height(LauncherSpace.sm))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(LauncherSpace.lg),
                verticalArrangement = Arrangement.spacedBy(LauncherSpace.xs),
            ) {
                QuietAction("Open screen usage") { viewModel.openUsage() }
                if (!state.usageAccess) {
                    QuietAction("Grant usage access") {
                        runCatching { context.startActivity(UsageRepository(context).settingsIntent()) }
                    }
                    QuietAction("Open App Info") {
                        openAppDetailsSettings(context)
                    }
                }
            }

            // ---- Apps ----------------------------------------------------------------

            Spacer(Modifier.height(LauncherSpace.xl))
            SectionHeading("Apps")
            Spacer(Modifier.height(LauncherSpace.sm))
            SecondaryText(
                "Open All apps to pin essentials to Home, hide apps, or long-press any app for quick actions.",
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
                    "Google Calendar access is off. Zendo Launcher reads synced Google Calendar " +
                        "events via Android's local provider only — offline, read-only."
                } else if (state.calendars.isEmpty()) {
                    "Access is granted, but no synced Google Calendar was found yet. " +
                        "Open Calendar to check account sync."
                } else if (state.settings.shownCalendarIds.isEmpty()) {
                    "Access is granted, but no Google calendars are chosen yet."
                } else {
                    "Reading ${state.settings.shownCalendarIds.size} of ${state.calendars.size} calendars, " +
                        "via local Google Calendar sync."
                },
            )
            Spacer(Modifier.height(LauncherSpace.sm))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(LauncherSpace.lg),
                verticalArrangement = Arrangement.spacedBy(LauncherSpace.xs),
            ) {
                if (!state.calendarPermission) {
                    CalendarAccessAction(viewModel, state.settings.calendarAsked)
                } else if (state.settings.shownCalendarIds.isEmpty() && state.calendars.any { it.isGoogle }) {
                    QuietAction("Use Google Calendars") { viewModel.selectGoogleCalendars() }
                }
                QuietAction("Open calendar") { viewModel.openCalendar() }
            }

            // ---- Data ----------------------------------------------------------------

            Spacer(Modifier.height(LauncherSpace.xl))
            SectionHeading("Data")
            Spacer(Modifier.height(LauncherSpace.sm))
            SecondaryText(
                "Everything stays on this device. A backup is one file you choose, holding " +
                    "your preferences, action, highlights, tasks and day records.",
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

            // ---- Mindful Gate --------------------------------------------------------

            Spacer(Modifier.height(LauncherSpace.xl))
            SectionHeading("Mindful Gate")
            Spacer(Modifier.height(LauncherSpace.sm))
            ToggleRow(
                label = "Pause before opening",
                checked = state.settings.pauseEnabled,
                onCheckedChange = { viewModel.setPauseEnabled(it) },
            )
            Spacer(Modifier.height(LauncherSpace.xs))
            SecondaryText(
                "Social media apps are selected automatically on first run. Opening a gated app " +
                    "pauses for a breath, asks why you're opening it, and offers a break.",
            )
            Spacer(Modifier.height(LauncherSpace.xs))
            QuietAction("Select detected social apps") { viewModel.selectDetectedSocialApps() }
            Spacer(Modifier.height(LauncherSpace.md))
            ChoiceRow(
                label = "Breath delay",
                options = MindfulGate.DELAY_OPTIONS_SECONDS.map { it to if (it == 0) "Off" else "${it}s" },
                selected = state.settings.pauseDelaySeconds,
                onSelect = { viewModel.setPauseDelaySeconds(it) },
            )
            Spacer(Modifier.height(LauncherSpace.md))
            ToggleRow(
                label = "Session timebox reminder",
                checked = state.settings.gateTimeboxEnabled,
                onCheckedChange = { viewModel.setGateTimeboxEnabled(it) },
            )
            Spacer(Modifier.height(LauncherSpace.xs))
            SecondaryText("Offer a 5m / 10m / 15m notification check-in when continuing.")
            Spacer(Modifier.height(LauncherSpace.md))
            PauseMessageField(viewModel, state.settings.pauseMessage)
            Spacer(Modifier.height(LauncherSpace.md))
            if (state.allApps.isEmpty()) {
                EmptyNote("No apps listed yet.")
            } else {
                val sortedApps = remember(state.allApps, state.settings.distract) {
                    state.allApps.sortedWith(
                        compareByDescending<com.zendolauncher.model.LaunchTarget> { it.key in state.settings.distract }
                            .thenByDescending { SocialAppDefaults.isSocialPackage(it.packageName) }
                            .thenBy { it.label.lowercase() },
                    )
                }
                sortedApps.forEachIndexed { index, target ->
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
                "Zendo Launcher ${BuildConfig.VERSION_NAME}, a standalone calm home screen.",
            )
            Spacer(Modifier.height(LauncherSpace.sm))
            QuietAction("Reopen onboarding") { viewModel.showOnboarding() }
            Spacer(Modifier.height(LauncherSpace.xl))
        }
    }
}

/**
 * Live preview inside Appearance Settings so users can immediately compare
 * Theme + Custom photo + Atmosphere (Window shadow / Washi linen / Morning mist) + Scrim.
 */
@Composable
private fun WallpaperPreviewCard(
    settings: LauncherSettings,
    customBitmap: Bitmap?,
) {
    val palette = LauncherColors.current
    val centered = settings.homeAlignment == HomeAlignment.CENTER
    val lowercase = settings.labelCase == LabelCase.LOWERCASE
    val shape = RoundedCornerShape(16.dp)
    val sampleApps = listOf("Instagram", "Calendar", "Notes").map {
        if (lowercase) it.lowercase() else it
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(172.dp)
            .clip(shape)
            .border(LauncherSpace.hairline, palette.separator, shape),
    ) {
        HomeBackdrop(
            style = settings.backdropStyle,
            scrimStrength = settings.scrimStrength,
            useCustomPhoto = settings.useCustomPhoto,
            blurPhoto = settings.backdropBlur,
            customBitmap = customBitmap,
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = LauncherSpace.lg, vertical = LauncherSpace.md),
        ) {
            Text(
                text = "09:41",
                style = LauncherType.clock.copy(fontSize = 28.sp),
                color = palette.primary,
            )
            Text(
                text = "10:00 · Deep work (in 19m)",
                style = LauncherType.secondary,
                color = palette.secondary,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            )
            Spacer(Modifier.height(LauncherSpace.sm))
            sampleApps.forEach { app ->
                Text(
                    text = app,
                    style = LauncherType.body,
                    color = palette.primary,
                    textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                )
            }
        }
    }
}

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
            "and takes the Mindful Gate for the rest of the day.",
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
    SecondaryText(
        "The time is read once when you tap an app here. Nothing runs in the background.",
    )
}

private const val WINDOW_STEP_MINUTES = 30

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

private fun openAppDetailsSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
