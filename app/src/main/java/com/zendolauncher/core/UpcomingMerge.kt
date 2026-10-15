package com.zendolauncher.core

import com.zendolauncher.model.CalendarEvent
import java.time.ZoneId

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
            .filter { it.endMillis >= nowMillis }
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
