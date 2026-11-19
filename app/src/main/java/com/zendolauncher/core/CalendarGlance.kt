package com.zendolauncher.core

import com.zendolauncher.model.CalendarEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class CalendarGlanceSummary(
    val headline: String,
    val subline: String? = null,
)

/**
 * Distils today's calendar events into one or two calm lines for the Home screen.
 * Pure Kotlin so every boundary (in-progress, upcoming, all-day, empty) is JVM-tested.
 */
object CalendarGlance {

    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

    fun summarize(
        events: List<CalendarEvent>,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): CalendarGlanceSummary {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val todaysEvents = events
            .filter { CalendarWeek.coversDate(it.startMillis, it.endMillis, today, zone) }
            .sortedBy { it.startMillis }

        if (todaysEvents.isEmpty()) {
            return CalendarGlanceSummary(headline = "Open day · nothing scheduled")
        }

        val timed = todaysEvents.filter { !it.allDay }
        val allDay = todaysEvents.filter { it.allDay }

        val inProgress = timed.firstOrNull { nowMillis >= it.startMillis && nowMillis < it.endMillis }
        val next = timed.firstOrNull { it.startMillis > nowMillis }

        if (inProgress != null) {
            val endText = formatTime(inProgress.endMillis, zone)
            val title = inProgress.title.ifBlank { "Untitled event" }
            val sub = next?.let {
                "Next ${formatTime(it.startMillis, zone)} · ${it.title.ifBlank { "Event" }}"
            } ?: allDay.firstOrNull()?.let { "All day · ${it.title.ifBlank { "Event" }}" }
            return CalendarGlanceSummary(
                headline = "Now · $title (until $endText)",
                subline = sub,
            )
        }

        if (next != null) {
            val startText = formatTime(next.startMillis, zone)
            val diffMin = ((next.startMillis - nowMillis) / 60_000L).coerceAtLeast(1L)
            val countdown = formatCountdown(diffMin)
            val title = next.title.ifBlank { "Untitled event" }
            val remainingCount = timed.count { it.startMillis > next.startMillis }
            val sub = when {
                remainingCount > 0 -> "+$remainingCount more later today"
                allDay.isNotEmpty() -> "All day · ${allDay.first().title.ifBlank { "Event" }}"
                else -> null
            }
            return CalendarGlanceSummary(
                headline = "$startText · $title ($countdown)",
                subline = sub,
            )
        }

        if (allDay.isNotEmpty()) {
            val first = allDay.first().title.ifBlank { "All-day event" }
            val extra = if (allDay.size > 1) "+${allDay.size - 1} more all-day" else null
            return CalendarGlanceSummary(headline = "Today · $first", subline = extra)
        }

        return CalendarGlanceSummary(headline = "Nothing else scheduled today")
    }

    private fun formatTime(millis: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalTime().format(timeFormatter)

    private fun formatCountdown(minutes: Long): String = when {
        minutes < 60L -> "in ${minutes}m"
        minutes % 60L == 0L -> "in ${minutes / 60L}h"
        else -> "in ${minutes / 60L}h ${minutes % 60L}m"
    }
}
