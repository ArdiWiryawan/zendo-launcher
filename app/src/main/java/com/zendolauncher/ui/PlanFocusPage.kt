package com.zendolauncher.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zendolauncher.core.FocusPhase
import com.zendolauncher.core.FocusSession
import com.zendolauncher.core.UpcomingItem
import com.zendolauncher.core.UpcomingMerge
import com.zendolauncher.ui.theme.LauncherColors
import com.zendolauncher.ui.theme.LauncherSpace
import com.zendolauncher.ui.theme.LauncherType
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Plan & Focus page: the merged agenda (real calendar occurrences + due
 * local tasks, sources labeled, never duplicated) above a real focus timer.
 * The timer is timestamp-based: it survives navigation and process death, is
 * recorded exactly once, and never needs calendar or usage permission.
 */
@Composable
fun PlanFocusPage(viewModel: LauncherViewModel, state: UiState) {
    val today = remember { LocalDate.now() }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = LauncherSpace.gutter),
    ) {
        Spacer(Modifier.height(LauncherSpace.xl))
        // Compact header: date, title, and the way out to the full view.
        SecondaryText(
            today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Plan & Focus",
                style = LauncherType.title,
                color = LauncherColors.current.primary,
                modifier = Modifier.weight(1f),
            )
            QuietAction("View calendar", onClick = { viewModel.openCalendar() })
        }
        Spacer(Modifier.height(LauncherSpace.md))
        SectionHeading("Upcoming")
        Spacer(Modifier.height(LauncherSpace.sm))
        AgendaSection(viewModel, state, today)
        Spacer(Modifier.height(LauncherSpace.lg))
        Hairline()
        Spacer(Modifier.height(LauncherSpace.md))
        SectionHeading("Focus")
        Spacer(Modifier.height(LauncherSpace.sm))
        FocusSection(viewModel, state)
        Spacer(Modifier.height(LauncherSpace.xl))
    }
}

@Composable
private fun AgendaSection(viewModel: LauncherViewModel, state: UiState, today: LocalDate) {
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    val now = System.currentTimeMillis()
    val items = remember(state.calendarEvents, state.tasks) {
        UpcomingMerge.next(state.calendarEvents, state.tasks, now, 3)
    }

    if (state.calendarLoading && items.isEmpty()) {
        SecondaryText("Loading…")
        return
    }
    state.calendarError?.let { error ->
        SecondaryText(error)
        if (items.isNotEmpty()) SecondaryText("Showing earlier results.")
        QuietAction("Retry", onClick = { viewModel.refreshCalendarEvents() })
        Spacer(Modifier.height(LauncherSpace.sm))
    }
    if (state.calendarPermission && state.settings.calendarGoogleOnly &&
        state.calendars.none { it.isGoogle }
    ) {
        EmptyNote("No Google calendars found. Check account sync, or turn off Google-only.")
        Spacer(Modifier.height(LauncherSpace.xs))
        QuietAction("Choose calendars", onClick = { viewModel.openCalendar() })
        return
    }
    if (items.isEmpty()) {
        if (!state.calendarPermission) {
            EmptyNote("Calendar access is off.")
            Spacer(Modifier.height(LauncherSpace.xs))
            SecondaryText("Due tasks would appear here too. Nothing is uploaded.")
            CalendarAccessAction(viewModel, state.settings.calendarAsked)
        } else {
            EmptyNote("No upcoming events")
            Spacer(Modifier.height(LauncherSpace.xs))
            QuietAction("View calendar", onClick = { viewModel.openCalendar() })
        }
        return
    }

    SecondaryText("Next ${items.size}")
    Spacer(Modifier.height(LauncherSpace.sm))
    items.forEach { item ->
        when (item) {
            is UpcomingItem.Event -> {
                val e = item.event
                TimelineEventRow(
                    startMillis = e.startMillis,
                    endMillis = e.endMillis,
                    title = e.title,
                    secondary = eventSecondary(e, today, zone),
                    inProgress = UpcomingMerge.isInProgress(e.startMillis, e.endMillis, now),
                    onOpen = {
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, viewModel.eventDetailsUri(e.id))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        } catch (_: ActivityNotFoundException) {
                        }
                    },
                )
            }
            is UpcomingItem.Task -> {
                TaskUpcomingRow(item.task.title, item.dueMillis)
                Spacer(Modifier.height(LauncherSpace.xs))
                QuietAction("Open task", onClick = { viewModel.openTaskEditor(item.task.id) })
            }
        }
        Spacer(Modifier.height(LauncherSpace.sm))
    }
    Spacer(Modifier.height(LauncherSpace.xs))
    Row(horizontalArrangement = Arrangement.spacedBy(LauncherSpace.lg)) {
        QuietAction("View calendar", onClick = { viewModel.openCalendar() })
        QuietAction("All tasks", onClick = { viewModel.openAllTasks() })
    }
}

/** Secondary line under an event: status first, then where it sits in time. */
private fun eventSecondary(
    e: com.zendolauncher.model.CalendarEvent,
    today: LocalDate,
    zone: ZoneId,
): String {
    val now = System.currentTimeMillis()
    val parts = ArrayList<String>()
    if (UpcomingMerge.isInProgress(e.startMillis, e.endMillis, now)) parts += "In progress"
    val date = Instant.ofEpochMilli(e.startMillis).atZone(zone).toLocalDate()
    if (date != today) parts += UpcomingMerge.dayLabel(date, today)
    if (!e.allDay) {
        val mins = ((e.endMillis - e.startMillis) / 60_000L).coerceAtLeast(1)
        parts += "$mins min"
    } else {
        parts += "All-day"
    }
    return parts.joinToString(" · ")
}

/**
 * One timeline row: consistent time column, a slim indicator (stronger for the
 * event in progress), and a title that truncates instead of breaking layout.
 */
@Composable
private fun TimelineEventRow(
    startMillis: Long,
    endMillis: Long,
    title: String,
    secondary: String,
    inProgress: Boolean,
    onOpen: () -> Unit,
) {
    val palette = LauncherColors.current
    val zone = remember { ZoneId.systemDefault() }
    val start = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalTime()
    val end = Instant.ofEpochMilli(endMillis).atZone(zone).toLocalTime()
    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm") }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .semantics {
                contentDescription = "$title, ${start.format(timeFmt)}" +
                    if (inProgress) ", in progress" else ""
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(horizontalAlignment = Alignment.Start, modifier = Modifier.width(52.dp)) {
            Text(text = start.format(timeFmt), style = LauncherType.body, color = palette.primary)
            SecondaryText(end.format(timeFmt))
        }
        Spacer(Modifier.width(LauncherSpace.sm))
        Box(
            Modifier
                .width(2.dp)
                .height(40.dp)
                .background(if (inProgress) palette.primary else palette.separator),
        )
        Spacer(Modifier.width(LauncherSpace.sm))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = LauncherType.body,
                color = palette.primary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            SecondaryText(secondary)
        }
    }
}

@Composable
private fun FocusSection(viewModel: LauncherViewModel, state: UiState) {
    val session = state.focusSession
    // Ticks once a second only while a segment runs; idle costs nothing.
    val now by produceState(initialValue = System.currentTimeMillis(), session) {
        if (session.phase == FocusPhase.IDLE) return@produceState
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000L - (value % 1_000L))
        }
    }
    val remaining = remember(session, now) { FocusSession.remaining(session, now) }

    // Natural end completes exactly once per segment: keyed on the segment start.
    var completedSegment by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(session.segmentStartMillis, remaining) {
        val start = session.segmentStartMillis
        if ((session.phase == FocusPhase.WORK || session.phase == FocusPhase.BREAK) &&
            remaining != null && remaining <= 0 && completedSegment != start
        ) {
            completedSegment = start
            viewModel.finishFocus(naturally = true)
        }
    }

    when (session.phase) {
        FocusPhase.IDLE -> FocusIdle(viewModel, state)
        FocusPhase.WORK -> FocusRunning(
            viewModel = viewModel,
            state = state,
            remaining = remaining,
            label = "Focusing",
            onPause = { viewModel.pauseFocus() },
            onFinish = { viewModel.finishFocus() },
        )
        FocusPhase.PAUSED -> {
            Text("Paused", style = LauncherType.title, color = LauncherColors.current.primary)
            Spacer(Modifier.height(LauncherSpace.xs))
            SecondaryText(remaining?.let { formatRemaining(it) } ?: "—")
            Spacer(Modifier.height(LauncherSpace.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(LauncherSpace.lg)) {
                QuietAction("Resume", onClick = { viewModel.resumeFocus() })
                QuietAction("Finish", onClick = { viewModel.finishFocus() })
            }
        }
        FocusPhase.BREAK -> FocusRunning(
            viewModel = viewModel,
            state = state,
            remaining = remaining,
            label = "Break",
            onPause = { viewModel.pauseFocus() },
            onFinish = { viewModel.finishFocus(naturally = true) },
        )
    }

    Spacer(Modifier.height(LauncherSpace.lg))
    FocusHistory(viewModel, state)
}

@Composable
private fun FocusIdle(viewModel: LauncherViewModel, state: UiState) {
    var work by remember(state.settings.focusWorkMinutes) { mutableStateOf(state.settings.focusWorkMinutes) }
    var rest by remember(state.settings.focusBreakMinutes) { mutableStateOf(state.settings.focusBreakMinutes) }
    StepperRow(
        label = "Work",
        valueText = "$work min",
        onDecrease = { work = (work - 5).coerceAtLeast(5); viewModel.setFocusDefaults(work, rest) },
        onIncrease = { work = (work + 5).coerceAtMost(180); viewModel.setFocusDefaults(work, rest) },
        decreaseLabel = "Shorter",
        increaseLabel = "Longer",
    )
    StepperRow(
        label = "Break",
        valueText = if (rest == 0) "Off" else "$rest min",
        onDecrease = { rest = (rest - 5).coerceAtLeast(0); viewModel.setFocusDefaults(work, rest) },
        onIncrease = { rest = (rest + 5).coerceAtMost(60); viewModel.setFocusDefaults(work, rest) },
        decreaseLabel = "Shorter",
        increaseLabel = "Longer",
    )
    Spacer(Modifier.height(LauncherSpace.sm))
    QuietAction("Start focus", onClick = { viewModel.startFocus(work, rest) })
    Spacer(Modifier.height(LauncherSpace.xs))
    SecondaryText("No calendar or usage access needed. The timer keeps time even if you leave.")
}

@Composable
private fun FocusRunning(
    viewModel: LauncherViewModel,
    state: UiState,
    remaining: Long?,
    label: String,
    onPause: () -> Unit,
    onFinish: () -> Unit,
) {
    val palette = LauncherColors.current
    var warningDismissed by remember { mutableStateOf(false) }
    Text(label, style = LauncherType.title, color = palette.primary)
    Spacer(Modifier.height(LauncherSpace.xs))
    Text(
        remaining?.let { formatRemaining(it) } ?: "—",
        style = LauncherType.clock.copy(fontSize = 40.sp),
        color = palette.primary,
        modifier = Modifier.semantics { contentDescription = "Time remaining" },
    )
    Spacer(Modifier.height(LauncherSpace.sm))
    Row(horizontalArrangement = Arrangement.spacedBy(LauncherSpace.lg)) {
        QuietAction("Pause", onClick = onPause)
        QuietAction("Finish", onClick = onFinish)
    }
    // Calendar context without calendar control: warn, never interrupt.
    val sessionEnd = state.focusSession.segmentStartMillis?.plus(state.focusSession.segmentLengthMillis)
    val nextEvent = remember(state.calendarEvents) {
        val now = System.currentTimeMillis()
        state.calendarEvents.filter { it.endMillis >= now }.minByOrNull { it.startMillis }
    }
    if (!warningDismissed && sessionEnd != null && nextEvent != null &&
        nextEvent.startMillis in System.currentTimeMillis() until sessionEnd
    ) {
        Spacer(Modifier.height(LauncherSpace.sm))
        SecondaryText(
            "${nextEvent.title} starts at ${
                Instant.ofEpochMilli(nextEvent.startMillis).atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("HH:mm"))
            } — inside this session.",
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(LauncherSpace.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuietAction("Continue", onClick = { warningDismissed = true })
            QuietAction("End session", onClick = onFinish)
        }
    }
    if (label == "Focusing" && state.settings.focusBreakMinutes > 0) {
        Spacer(Modifier.height(LauncherSpace.sm))
        QuietAction("Take break now", onClick = { viewModel.startFocusBreak() })
    }
}

@Composable
private fun FocusHistory(viewModel: LauncherViewModel, state: UiState) {
    val history = state.focusHistory.takeLast(3).reversed()
    if (history.isEmpty()) {
        EmptyNote("No completed sessions yet.")
        return
    }
    SectionHeading("Recent sessions")
    Spacer(Modifier.height(LauncherSpace.xs))
    history.forEach { record ->
        val date = Instant.ofEpochMilli(record.startedAtMillis).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("d MMM, HH:mm"))
        SecondaryText(
            "$date · ${record.plannedMinutes} min planned · " +
                "${(record.workedMillis / 60_000).toInt()} min worked" +
                if (record.finishedNaturally) "" else " · ended early",
        )
    }
    Spacer(Modifier.height(LauncherSpace.xs))
    QuietAction("Clear history", onClick = { viewModel.clearFocusHistory() })
}

/** A due local task row: due date on the left, title plus a quiet source tag. */
@Composable
private fun TaskUpcomingRow(title: String, dueMillis: Long) {
    val palette = LauncherColors.current
    val zone = ZoneId.systemDefault()
    val due = Instant.ofEpochMilli(dueMillis).atZone(zone)
    Row {
        Column(horizontalAlignment = Alignment.Start, modifier = Modifier.width(52.dp)) {
            Text(
                text = due.format(DateTimeFormatter.ofPattern("d MMM")),
                style = LauncherType.body,
                color = palette.primary,
            )
            SecondaryText("Due")
        }
        Spacer(Modifier.width(LauncherSpace.md))
        Column(Modifier.weight(1f)) {
            Text(text = title, style = LauncherType.body, color = palette.primary)
            SecondaryText("Task")
        }
    }
}

/** mm:ss readout, floored at zero. Pure for tests. */
internal fun formatRemaining(millis: Long): String {
    val total = (millis / 1_000L).coerceAtLeast(0L)
    val mm = (total / 60).toString().padStart(2, '0')
    val ss = (total % 60).toString().padStart(2, '0')
    return "$mm:$ss"
}
