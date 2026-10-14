package com.zendolauncher.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.sp
import com.zendolauncher.model.AgendaItem
import com.zendolauncher.model.ZendoToday
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
 * The launcher's most important page, and its job is to let the user act and leave:
 * clock and date first, optional daily context, the pinned apps, then one route to the
 * drawer. The clock is the only large element and empty space is left empty, so nothing
 * is added to fill the screen.
 */
@Composable
fun HomeScreen(viewModel: LauncherViewModel, state: UiState) {
    val palette = LauncherColors.current
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val heightDp = with(density) { windowInfo.containerSize.height.toDp() }
    // A fraction of the real window height rather than a fixed step, so the block below
    // sits at the same height on a short screen and a tall one. The spacing scale is the
    // floor and the ceiling, because a fraction of a very short or very tall window is
    // either cramped or a gap nobody asked for.
    val topGap = (heightDp * 0.06f).coerceIn(LauncherSpace.lg, LauncherSpace.xl)
    // Measured here and handed to the clock, which needs the same width to size itself.
    val widthDp = with(density) { windowInfo.containerSize.width.toDp().value }

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
            // The status-bar inset is already consumed above, so this is the clock's
            // breathing room on top of it. The size is not a guess: see topGap above.
            Spacer(Modifier.height(topGap))
            Clock(state.settings.clock24h, widthDp)
            Spacer(Modifier.height(LauncherSpace.xs))
            Text(
                text = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", currentLocale())),
                style = LauncherType.date,
                color = palette.secondary,
            )

            if (state.settings.todayEnabled) {
                state.zendo?.takeUnless { it.isEmpty }?.let { TodayBlock(it) }
            }

            Spacer(Modifier.height(LauncherSpace.xl))
            SectionHeading("Essentials")
            Spacer(Modifier.height(LauncherSpace.sm))

            if (state.favourites.isEmpty()) {
                EmptyNote("No pinned apps yet.")
            } else {
                state.favourites.forEach { target ->
                    AppRow(
                        viewModel = viewModel,
                        target = target,
                        layout = state.settings.layout,
                        isFavourite = true,
                        onClick = { viewModel.launch(target) },
                        onToggleFavourite = { viewModel.toggleFavourite(target.key) },
                        onToggleHidden = { viewModel.toggleHidden(target.key) },
                    )
                }
            }

            Spacer(Modifier.height(LauncherSpace.xl))
        }

        state.undoHidden?.let {
            // The gutter lives on the scrolling column above, not on this one, so the note
            // has to carry its own or its text starts flush against the screen edge.
            UndoNote(
                text = "Hidden ${it.label}.",
                actionLabel = "Undo",
                onAction = { viewModel.undoHide() },
                modifier = Modifier.padding(horizontal = LauncherSpace.gutter),
            )
            Spacer(Modifier.height(LauncherSpace.sm))
        }

        // One mechanism for the bar's spacing: two actions packed from the same left edge
        // as everything above, not spread by a weight spacer.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = LauncherSpace.gutter, vertical = LauncherSpace.md),
            horizontalArrangement = Arrangement.spacedBy(LauncherSpace.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuietAction("All apps", onClick = { viewModel.openApps() })
            QuietAction("Settings", onClick = { viewModel.openSettings() })
        }
    }
}

/**
 * Ticks on the minute boundary rather than every second: the display only ever shows
 * minutes, and a home screen that wakes the CPU 86,400 times a day is not calm.
 *
 * The size is fitted to the screen instead of fixed, because at a raised system font
 * scale a 60.sp clock is wider than the text column and clips. [widthDp] is the window's
 * measured width, which is what the text column actually has to fit into.
 */
@Composable
private fun Clock(use24Hour: Boolean, widthDp: Float) {
    val now by produceState(initialValue = LocalTime.now()) {
        while (true) {
            val current = LocalTime.now()
            value = current
            delay(1_000L * (60 - current.second))
        }
    }
    val locale = currentLocale()
    // LocalConfiguration is snapshot-backed, so a font-scale change re-runs this. The
    // width is not read from it: LocalWindowInfo carries the real container size.
    val configuration = LocalConfiguration.current
    val clockStyle = LauncherType.clock.copy(
        fontSize = clockSizeSp(widthDp.toInt(), configuration.fontScale).sp,
    )
    val palette = LauncherColors.current
    val pattern = if (use24Hour) "HH:mm" else "h:mm"
    val text = now.format(DateTimeFormatter.ofPattern(pattern, locale))
    Row(verticalAlignment = Alignment.Bottom) {
        Text(text = text, style = clockStyle, color = palette.primary, maxLines = 1)
        if (!use24Hour) {
            Text(
                text = now.format(DateTimeFormatter.ofPattern("a", locale)),
                style = LauncherType.secondary,
                color = palette.secondary,
                // Lifts the smaller suffix onto the clock's baseline.
                modifier = Modifier.padding(start = LauncherSpace.sm, bottom = LauncherSpace.md),
            )
        }
    }
}

/**
 * The current locale as observable state. Reading Locale.getDefault() inside a
 * composable would not re-run when the user changes locale, leaving a stale date
 * format on screen until the next process start.
 */
@Composable
private fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

/**
 * The day's context, reduced to the two lines that change what the user does next: the
 * one action, falling back to the day's highlight when there is no action, and what is
 * coming. The model also carries `practices`, a list to read rather than to glance at,
 * so it is left to the surface built for planning.
 */
@Composable
private fun TodayBlock(today: ZendoToday) {
    val action = today.mainAction ?: today.highlight
    val agenda = today.nextAgenda
    // A block carrying only `practices` is not `isEmpty`, but it has nothing this screen
    // shows, and a heading over nothing reads as a broken screen.
    if (action == null && agenda == null) return

    val palette = LauncherColors.current
    Spacer(Modifier.height(LauncherSpace.xl))
    SectionHeading("Today")
    action?.let {
        Spacer(Modifier.height(LauncherSpace.sm))
        Text(text = it, style = LauncherType.body, color = palette.primary)
    }
    agenda?.let {
        Spacer(Modifier.height(LauncherSpace.xs))
        SecondaryText(it.display())
    }
}

private fun AgendaItem.display(): String = if (time.isNullOrBlank()) title else "$title · $time"
