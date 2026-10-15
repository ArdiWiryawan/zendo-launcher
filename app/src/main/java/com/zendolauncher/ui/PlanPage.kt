package com.zendolauncher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.zendolauncher.core.CalendarWeek
import com.zendolauncher.ui.theme.LauncherColors
import com.zendolauncher.ui.theme.LauncherSpace
import com.zendolauncher.ui.theme.LauncherType
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * The Plan page: one date, its full agenda, and a compact way to move between days.
 * Everything here is read from the device calendar provider, offline, and only for
 * calendars the user chose. No focus timer lives here; that belongs to the Zendo app.
 *
 * The states are deliberate and distinct: no permission, no calendars on the device,
 * nothing chosen yet, a sync warning, an error with stale results kept, and the plain
 * quiet day. A page that said "nothing" for all of these would lie about half of them.
 */
@Composable
fun PlanPage(viewModel: LauncherViewModel, state: UiState) {
    val palette = LauncherColors.current
    val selected = remember(state.selectedDateEpochDay) {
        LocalDate.ofEpochDay(state.selectedDateEpochDay)
    }
    val today = LocalDate.now()
    val zone = remember { ZoneId.systemDefault() }
    val events = remember(state.calendarEvents, selected) {
        state.calendarEvents.filter {
            CalendarWeek.coversDate(it.startMillis, it.endMillis, selected, zone)
        }.sortedBy { it.startMillis }
    }
    // Ticks on the minute so the in-progress marker is honest without waking the CPU
    // per second.
    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(60_000L)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshCalendarPermission()
        if (state.calendars.isEmpty()) {
            viewModel.refreshCalendar()
        } else {
            viewModel.onPlanPageVisible()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = LauncherSpace.gutter),
    ) {
        Spacer(Modifier.height(LauncherSpace.xl))
        Text("Plan", style = LauncherType.title, color = palette.primary)
        Spacer(Modifier.height(LauncherSpace.md))

        // Compact date navigation: one day back, one day forward, and the date itself
        // in the middle. The full week strip lives in the Calendar screen.
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuietAction("Earlier", onClick = { viewModel.selectDate(selected.minusDays(1)) })
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (selected == today) {
                        "Today"
                    } else {
                        selected.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))
                    },
                    style = LauncherType.body,
                    color = palette.primary,
                )
                SecondaryText(
                    selected.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
                )
            }
            QuietAction("Later", onClick = { viewModel.selectDate(selected.plusDays(1)) })
        }
        if (selected != today) {
            QuietAction("Back to today", onClick = { viewModel.selectDate(today) })
        }

        Spacer(Modifier.height(LauncherSpace.md))
        Hairline()
        Spacer(Modifier.height(LauncherSpace.md))

        when {
            !state.calendarPermission -> {
                EmptyNote("The launcher reads your device calendar only, offline. Nothing is uploaded.")
                Spacer(Modifier.height(LauncherSpace.xs))
                SecondaryText("Permission is asked only when you open the calendar.")
                CalendarAccessAction(viewModel, state.settings.calendarAsked)
            }

            state.calendars.isEmpty() && state.calendarLoading -> {
                SecondaryText("Loading calendars…")
            }

            state.calendars.isEmpty() -> {
                EmptyNote("No calendars are visible to Android yet.")
                Spacer(Modifier.height(LauncherSpace.xs))
                SecondaryText(
                    "If your events live in another app, set that account up on this device first.",
                )
                QuietAction("Check again", onClick = { viewModel.refreshCalendar() })
            }

            state.settings.shownCalendarIds.isEmpty() -> {
                EmptyNote("No calendars chosen yet. The agenda stays empty until you choose.")
                Spacer(Modifier.height(LauncherSpace.xs))
                SecondaryText("This is deliberate: nothing is read until you pick it.")
                QuietAction("Choose calendars", onClick = { viewModel.openCalendar() })
            }

            else -> {
                // Success replaces the previous answer; a failure keeps it, marked stale.
                state.calendarError?.let { error ->
                    SecondaryText(error)
                    if (events.isNotEmpty()) SecondaryText("Showing earlier results.")
                    QuietAction("Retry", onClick = { viewModel.refreshCalendarEvents() })
                    Spacer(Modifier.height(LauncherSpace.sm))
                }
                if (events.isEmpty() && state.calendarLoading) {
                    SecondaryText("Loading…")
                } else if (events.isEmpty()) {
                    EmptyNote(
                        if (selected == today) "Nothing today. The day is open." else "Nothing on this date.",
                    )
                } else {
                    events.forEach { event ->
                        val inProgress = !event.allDay &&
                            now >= event.startMillis && now < event.endMillis &&
                            selected == today
                        UpcomingRow(
                            startMillis = event.startMillis,
                            endMillis = event.endMillis,
                            title = event.title,
                            allDay = event.allDay,
                            inProgress = inProgress,
                        )
                        Spacer(Modifier.height(LauncherSpace.md))
                    }
                }
                if (state.calendars.isNotEmpty() && state.calendars.none { it.syncEvents }) {
                    SecondaryText(
                        "Sync looks off for these calendars, so new events may not arrive. " +
                            "Check account sync in system Settings.",
                    )
                }
            }
        }

        Spacer(Modifier.height(LauncherSpace.lg))
        Row(horizontalArrangement = Arrangement.spacedBy(LauncherSpace.lg)) {
            QuietAction("Open calendar", onClick = { viewModel.openCalendar() })
            QuietAction("Refresh", onClick = { viewModel.refreshCalendar() })
        }
        Spacer(Modifier.height(LauncherSpace.xl))
    }
}
