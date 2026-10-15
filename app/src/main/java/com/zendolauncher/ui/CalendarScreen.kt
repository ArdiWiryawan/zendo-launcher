package com.zendolauncher.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.CalendarContract
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.zendolauncher.core.CalendarWeek
import com.zendolauncher.ui.theme.LauncherColors
import com.zendolauncher.ui.theme.LauncherSpace
import com.zendolauncher.ui.theme.LauncherType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Full agenda: week strip + events for the selected date + calendar selection.
 * Home's Upcoming stays focused on the next few; the month grid lives here as a
 * strip, not forced into the widget.
 */
@Composable
fun CalendarScreen(viewModel: LauncherViewModel, state: UiState) {
    val palette = LauncherColors.current
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.refreshCalendar() }

    val selected = remember(state.selectedDateEpochDay) {
        LocalDate.ofEpochDay(state.selectedDateEpochDay)
    }
    val week = remember(selected) { CalendarWeek.weekFor(selected) }
    val today = remember { LocalDate.now() }
    val zone = remember { ZoneId.systemDefault() }
    val events = remember(state.calendarEvents, selected) {
        state.calendarEvents.filter {
            CalendarWeek.coversDate(it.startMillis, it.endMillis, selected, zone)
        }.sortedBy { it.startMillis }
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
            ScreenTitle("Calendar") { QuietAction("Home", onClick = { viewModel.goHome() }) }
            Spacer(Modifier.height(LauncherSpace.sm))
            Text(
                selected.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())).uppercase(),
                style = LauncherType.secondary,
                color = palette.secondary,
            )
            Spacer(Modifier.height(LauncherSpace.sm))
            Text("Your week", style = LauncherType.title, color = palette.primary)
            Spacer(Modifier.height(LauncherSpace.md))
            WeekStrip(week, selected, today, hasEvent = { date ->
                state.calendarEvents.any {
                    CalendarWeek.coversDate(it.startMillis, it.endMillis, date, zone)
                }
            }, onSelect = { viewModel.selectDate(it) })
            Spacer(Modifier.height(LauncherSpace.lg))
            Hairline()
            Spacer(Modifier.height(LauncherSpace.md))

            if (!state.calendarPermission) {
                SectionHeading("Calendar access")
                Spacer(Modifier.height(LauncherSpace.sm))
                EmptyNote("The launcher reads your device calendar only, offline. Nothing is uploaded.")
                Spacer(Modifier.height(LauncherSpace.xs))
                SecondaryText("Permission is asked only here, when you open the calendar.")
                CalendarAccessAction(viewModel, state.settings.calendarAsked)
            } else {
                val label = when (selected) {
                    today -> "TODAY · ${events.size} EVENTS"
                    else -> "${selected.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())).uppercase()} · ${events.size}"
                }
                SectionHeading(label)
                Spacer(Modifier.height(LauncherSpace.sm))
                if (events.isEmpty()) {
                    EmptyNote(
                        if (selected == today) "Nothing today. The day is open."
                        else "Nothing on this date.",
                    )
                } else {
                    events.forEach { event ->
                        UpcomingRow(event.startMillis, event.endMillis, event.title, event.allDay)
                        Spacer(Modifier.height(LauncherSpace.md))
                    }
                }
                Spacer(Modifier.height(LauncherSpace.md))
                Row(horizontalArrangement = Arrangement.spacedBy(LauncherSpace.lg)) {
                    QuietAction("Refresh", onClick = { viewModel.refreshCalendar() })
                    QuietAction("Open system calendar", onClick = {
                        val millis = selected.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        val uri = CalendarContract.CONTENT_URI.buildUpon()
                            .appendPath("time").appendPath(millis.toString()).build()
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW).setData(uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (_: ActivityNotFoundException) {
                            try {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW).setType("vnd.android.cursor.item/event")
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            } catch (_: ActivityNotFoundException) {
                            }
                        }
                    })
                }
                if (state.calendars.size > 1) {
                    Spacer(Modifier.height(LauncherSpace.xl))
                    SectionHeading("Calendars")
                    Spacer(Modifier.height(LauncherSpace.sm))
                    SecondaryText("Uncheck to hide a calendar from Upcoming.")
                    state.calendars.forEach { cal ->
                        val shown = state.settings.shownCalendarIds.isEmpty() || cal.id in state.settings.shownCalendarIds
                        ToggleRow(
                            label = cal.name,
                            checked = shown,
                            onCheckedChange = { on ->
                                val current = state.settings.shownCalendarIds.toMutableSet()
                                if (state.settings.shownCalendarIds.isEmpty()) {
                                    // First narrowing: start from all, then remove.
                                    current.addAll(state.calendars.map { it.id })
                                }
                                if (on) current.add(cal.id) else current.remove(cal.id)
                                // All shown == empty filter (same meaning, less storage).
                                val allIds = state.calendars.map { it.id }.toSet()
                                viewModel.setShownCalendars(if (current == allIds) emptySet() else current)
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(LauncherSpace.xl))
        }
    }
}

@Composable
private fun WeekStrip(
    week: List<LocalDate>,
    selected: LocalDate,
    today: LocalDate,
    hasEvent: (LocalDate) -> Boolean,
    onSelect: (LocalDate) -> Unit,
) {
    val palette = LauncherColors.current
    val dayLetter = DateTimeFormatter.ofPattern("E", Locale.getDefault())
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        week.forEach { date ->
            val isSelected = date == selected
            val isToday = date == today
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(44.dp)
                    .clickable(onClick = { onSelect(date) })
                    .semantics {
                        contentDescription = date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM"))
                        stateDescription = when {
                            isSelected -> "Selected"
                            isToday -> "Today"
                            else -> "Not selected"
                        }
                    }
                    .padding(vertical = 4.dp),
            ) {
                SecondaryText(date.format(dayLetter).take(1).uppercase())
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) palette.primary else palette.background),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        date.dayOfMonth.toString(),
                        style = LauncherType.body,
                        color = if (isSelected) palette.background else palette.primary,
                    )
                }
                // Dot marks event days; selected/today already announced via stateDescription.
                Box(Modifier.size(4.dp)) {
                    if (hasEvent(date)) {
                        Box(
                            Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(palette.secondary),
                        )
                    }
                }
                if (isToday && !isSelected) {
                    SecondaryText("today")
                }
            }
        }
    }
}
