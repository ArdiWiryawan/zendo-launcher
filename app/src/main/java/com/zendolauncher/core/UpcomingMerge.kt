package com.zendolauncher.core

import com.zendolauncher.model.CalendarEvent
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One row of the merged Upcoming view. Calendar rows and local task rows stay
 * visibly distinct (source tag) and are never merged into duplicate records:
 * a task never becomes an event and an event never becomes a task.
 */
sealed interface UpcomingItem {
    val sortKey: Long
    data class Event(val event: CalendarEvent) : UpcomingItem {
        override val sortKey: Long get() = event.startMillis
    }
    data class Task(val task: TaskItem, val dueMillis: Long) : UpcomingItem {
        override val sortKey: Long get() = dueMillis
    }
}

object UpcomingMerge {

    /**
     * Conservative end for events without usable end times (missing or
     * zero-length): one hour from the start. Such an event stays visible until
     * an hour past its start instead of vanishing as already-ended, and never
     * stretches indefinitely.
     */
    fun effectiveEnd(startMillis: Long, endMillis: Long): Long =
        if (endMillis > startMillis) endMillis else startMillis + 3_600_000L

    /** In-progress when now falls inside [start, effective end). */
    fun isInProgress(startMillis: Long, endMillis: Long, nowMillis: Long): Boolean =
        nowMillis >= startMillis && nowMillis < effectiveEnd(startMillis, endMillis)

    /** "Today", "Tomorrow", or "d MMM" in the device locale. Pure for tests. */
    fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
    }

    /**
     * Staleness rule for agenda refreshes: refresh when never loaded or older
     * than [staleAfterMillis] (default a minute). Pure for tests.
     */
    fun calendarStale(
        lastRefreshMillis: Long,
        nowMillis: Long,
        staleAfterMillis: Long = 60_000L,
    ): Boolean = lastRefreshMillis <= 0L || nowMillis - lastRefreshMillis > staleAfterMillis

    /**
     * Next items from now: unfinished calendar occurrences plus due-dated,
     * unfinished, Home-visible local tasks. Undated tasks are excluded — they
     * live in the task list, not in time. Pure for testability.
     */
    fun next(
        events: List<CalendarEvent>,
        tasks: List<TaskItem>,
        nowMillis: Long,
        limit: Int,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<UpcomingItem> {
        val upcomingEvents = events
            .filter { effectiveEnd(it.startMillis, it.endMillis) >= nowMillis }
            .map { UpcomingItem.Event(it) }
        val dueTasks = tasks
            .filter { !it.done && it.showOnHome && it.dueDateMillis != null && it.dueDateMillis >= startOfToday(nowMillis, zone) }
            .map { UpcomingItem.Task(it, it.dueDateMillis!!) }
        return (upcomingEvents + dueTasks).sortedBy { it.sortKey }.take(limit)
    }

    private fun startOfToday(nowMillis: Long, zone: ZoneId): Long =
        java.time.Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
            .atStartOfDay(zone).toInstant().toEpochMilli()
}
