package com.zendolauncher.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zendolauncher.core.CalendarGlance
import com.zendolauncher.core.PageType
import com.zendolauncher.core.UsageCalculator
import com.zendolauncher.data.HomeAlignment
import com.zendolauncher.data.LabelCase
import com.zendolauncher.ui.theme.HomeBackdrop
import com.zendolauncher.ui.theme.LauncherColors
import com.zendolauncher.ui.theme.LauncherSpace
import com.zendolauncher.ui.theme.LauncherType
import com.zendolauncher.ui.theme.clockSizeSp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Quiet Home: clock, date, Google Calendar glance, optional screen-time whisper,
 * clean pinned apps (centered or left-aligned), and minimal drawer/settings chrome.
 */
@Composable
fun HomeScreen(viewModel: LauncherViewModel, state: UiState) {
    val palette = LauncherColors.current

    LaunchedEffect(state.calendarPermission, state.settings.showCalendarGlance) {
        if (state.calendarPermission && state.settings.showCalendarGlance && state.calendars.isEmpty()) {
            viewModel.refreshCalendar()
        }
    }

    Box(Modifier.fillMaxSize()) {
        HomeBackdrop(
            style = state.settings.backdropStyle,
            scrimStrength = state.settings.scrimStrength,
            useCustomPhoto = state.settings.useCustomPhoto,
            blurPhoto = state.settings.backdropBlur,
            customBitmap = state.customWallpaperBitmap,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding(),
        ) {
            QuietHomePage(
                viewModel = viewModel,
                state = state,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )

            state.undoHidden?.let {
                UndoNote(
                    text = "Hidden ${it.label}.",
                    actionLabel = "Undo",
                    onAction = { viewModel.undoHide() },
                    modifier = Modifier.padding(horizontal = LauncherSpace.gutter),
                )
                Spacer(Modifier.height(LauncherSpace.sm))
            }

            state.pinNotice?.let {
                SecondaryText(it, modifier = Modifier.padding(horizontal = LauncherSpace.gutter))
                Spacer(Modifier.height(LauncherSpace.sm))
            }

            val pillShape = RoundedCornerShape(999.dp)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = LauncherSpace.gutter)
                    .height(LauncherSpace.touchTarget)
                    .clip(pillShape)
                    .border(LauncherSpace.hairline, palette.separator, pillShape)
                    .clickable(role = Role.Button) { viewModel.openApps(autoFocusSearch = true) },
            ) {
                Text("Search apps", style = LauncherType.action, color = palette.secondary)
            }
            Spacer(Modifier.height(LauncherSpace.sm))
            Row(
                Modifier
                    .fillMaxWidth()
                    .swipeUpToOpen { viewModel.openApps() }
                    .padding(horizontal = LauncherSpace.gutter, vertical = LauncherSpace.md),
                horizontalArrangement = Arrangement.spacedBy(LauncherSpace.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QuietAction("All apps", onClick = { viewModel.openApps() })
                QuietAction("Calendar", onClick = { viewModel.openCalendar() })
                QuietAction("Usage", onClick = { viewModel.openUsage() })
                QuietAction("Settings", onClick = { viewModel.openSettings(Screen.HOME) })
            }
        }
    }
}

@Composable
private fun QuietHomePage(
    viewModel: LauncherViewModel,
    state: UiState,
    modifier: Modifier = Modifier,
) {
    val palette = LauncherColors.current
    val centered = state.settings.homeAlignment == HomeAlignment.CENTER
    val lowercase = state.settings.labelCase == LabelCase.LOWERCASE
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val heightDp = with(density) { windowInfo.containerSize.height.toDp() }
    val widthDp = with(density) { windowInfo.containerSize.width.toDp().value }
    val topGap = (heightDp * 0.05f).coerceIn(LauncherSpace.md, LauncherSpace.xl)

    val appsPage = state.settings.home.pages.firstOrNull { it.type == PageType.APPS }
    val byKey = remember(state.visibleApps) { state.visibleApps.associateBy { it.key } }
    val targets = remember(appsPage, byKey) {
        appsPage?.config?.itemKeys?.mapNotNull { byKey[it] }.orEmpty()
    }

    Column(
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = LauncherSpace.gutter),
    ) {
        Spacer(Modifier.height(topGap))
        Clock(widthDp)
        Spacer(Modifier.height(LauncherSpace.xs))
        Text(
            text = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", currentLocale())),
            style = LauncherType.date,
            color = palette.secondary,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        )

        if (state.settings.showCalendarGlance) {
            Spacer(Modifier.height(LauncherSpace.md))
            CalendarGlanceBlock(
                state = state,
                centered = centered,
                onClick = { viewModel.openCalendar() },
            )
        }

        if (state.settings.showUsageOnHome && state.usageAccess && state.dailyUsage.totalForegroundMillis > 0L) {
            Spacer(Modifier.height(LauncherSpace.xs))
            val totalText = UsageCalculator.formatDuration(state.dailyUsage.totalForegroundMillis)
            val whisper = if (lowercase) "screen time · $totalText" else "Screen time · $totalText"
            Text(
                text = whisper,
                style = LauncherType.secondary,
                color = palette.secondary,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.clickable(role = Role.Button) { viewModel.openUsage() },
            )
        }

        Spacer(Modifier.height(LauncherSpace.lg))

        if (targets.isEmpty()) {
            EmptyNote(
                text = "No apps on Home yet. Open All apps and pin the essentials.",
                modifier = Modifier.fillMaxWidth(),
            )
            QuietAction("Open All apps", onClick = { viewModel.openApps() })
        } else {
            targets.forEachIndexed { index, target ->
                val usageWhisper = if (state.settings.showUsageOnHome && state.usageAccess) {
                    state.dailyUsage.byPackage[target.packageName]
                        ?.takeIf { it.foregroundMillis >= 60_000L }
                        ?.let { UsageCalculator.formatDuration(it.foregroundMillis) }
                } else {
                    null
                }
                AppRow(
                    viewModel = viewModel,
                    target = target,
                    isFavourite = true,
                    onClick = { viewModel.launch(target) },
                    onToggleFavourite = { viewModel.toggleFavourite(target.key) },
                    onToggleHidden = { viewModel.toggleHidden(target.key) },
                    showPinAction = false,
                    showFavouriteDot = false,
                    lowercaseLabel = lowercase,
                    centered = centered,
                    usageWhisper = usageWhisper,
                    isGated = target.key in state.settings.distract,
                    onToggleGate = {
                        if (!state.settings.pauseEnabled) viewModel.setPauseEnabled(true)
                        viewModel.toggleDistract(target.key)
                    },
                    onMoveUp = if (index > 0) ({ viewModel.reorderPinnedOnHome(target.key, -1) }) else null,
                    onMoveDown = if (index < targets.lastIndex) ({ viewModel.reorderPinnedOnHome(target.key, 1) }) else null,
                    longPressMenu = true,
                )
            }
        }
        Spacer(Modifier.height(LauncherSpace.xl))
    }
}

@Composable
private fun CalendarGlanceBlock(
    state: UiState,
    centered: Boolean,
    onClick: () -> Unit,
) {
    val palette = LauncherColors.current
    val align = if (centered) TextAlign.Center else TextAlign.Start
    Column(
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        modifier = Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = LauncherSpace.xs),
    ) {
        when {
            !state.calendarPermission -> {
                Text(
                    text = "Tap to connect Google Calendar",
                    style = LauncherType.secondary,
                    color = palette.secondary,
                    textAlign = align,
                )
            }
            state.calendarLoading && state.calendarEvents.isEmpty() -> {
                Text(
                    text = "Syncing Google Calendar…",
                    style = LauncherType.secondary,
                    color = palette.secondary,
                    textAlign = align,
                )
            }
            state.settings.shownCalendarIds.isEmpty() -> {
                Text(
                    text = "Tap to select Google Calendar",
                    style = LauncherType.secondary,
                    color = palette.secondary,
                    textAlign = align,
                )
            }
            else -> {
                val summary = remember(state.calendarEvents) {
                    CalendarGlance.summarize(state.calendarEvents, System.currentTimeMillis())
                }
                Text(
                    text = summary.headline,
                    style = LauncherType.body,
                    color = palette.primary,
                    textAlign = align,
                )
                summary.subline?.let {
                    Text(
                        text = it,
                        style = LauncherType.secondary,
                        color = palette.secondary,
                        textAlign = align,
                    )
                }
            }
        }
    }
}

private fun Modifier.swipeUpToOpen(onSwipeUp: () -> Unit): Modifier =
    this.pointerInput(onSwipeUp) {
        var accumulated = 0f
        detectVerticalDragGestures(
            onDragStart = { accumulated = 0f },
            onVerticalDrag = { change, dragAmount ->
                accumulated += dragAmount
                if (accumulated < -120f) {
                    change.consume()
                }
            },
            onDragEnd = {
                if (accumulated < -120f) onSwipeUp()
                accumulated = 0f
            },
            onDragCancel = { accumulated = 0f },
        )
    }

@Composable
private fun Clock(widthDp: Float) {
    val now by produceState(initialValue = LocalTime.now()) {
        while (true) {
            val current = LocalTime.now()
            value = current
            delay(1_000L * (60 - current.second))
        }
    }
    val locale = currentLocale()
    val configuration = LocalConfiguration.current
    val clockStyle = LauncherType.clock.copy(
        fontSize = clockSizeSp(widthDp.toInt(), configuration.fontScale).sp,
    )
    val palette = LauncherColors.current
    Text(
        text = now.format(DateTimeFormatter.ofPattern("HH:mm", locale)),
        style = clockStyle,
        color = palette.primary,
        maxLines = 1,
    )
}

@Composable
private fun currentLocale(): Locale = LocalConfiguration.current.locales[0]
