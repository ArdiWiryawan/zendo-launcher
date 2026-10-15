package com.zendolauncher.core

import com.zendolauncher.model.CalendarEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class UpcomingMergeTest {

    private fun event(title: String, start: Long, end: Long) =
        CalendarEvent(id = title.hashCode().toLong(), title = title, startMillis = start, endMillis = end)

    private fun task(id: String, title: String, due: Long?, done: Boolean = false, show: Boolean = true) =
        TaskItem(id = id, title = title, done = done, showOnHome = show, dueDateMillis = due)

    private val now = 1_790_000_000_000L

    @Test
    fun `merges chronologically with sources distinct and undated tasks excluded`() {
        val events = listOf(
            event("Later", now + 3_600_000L, now + 7_200_000L),
            event("Past", now - 7_200_000L, now - 3_600_000L),
            event("Sooner", now + 1_800_000L, now + 3_000_000L),
        )
        val tasks = listOf(
            task("t1", "Due between", now + 2_000_000L),
            task("t2", "No date", null),
            task("t3", "Done due", now + 100_000L, done = true),
            task("t4", "Hidden due", now + 100_000L, show = false),
        )
        val out = UpcomingMerge.next(events, tasks, now, limit = 10)
        assertEquals(3, out.size)
        assertTrue(out[0] is UpcomingItem.Event && (out[0] as UpcomingItem.Event).event.title == "Sooner")
        assertTrue(out[1] is UpcomingItem.Task)
        assertTrue(out[2] is UpcomingItem.Event && (out[2] as UpcomingItem.Event).event.title == "Later")
    }

    @Test
    fun `limit and past-due cutoff hold`() {
        val tasks = (1..10).map { task("t$it", "T$it", now + it * 60_000L) }
        assertEquals(3, UpcomingMerge.next(emptyList(), tasks, now, limit = 3).size)
        val overdue = listOf(task("old", "Old", now - 86_400_000L))
        assertTrue(UpcomingMerge.next(emptyList(), overdue, now, limit = 10).isEmpty())
    }

    @Test
    fun `coversDate spans multi-day events and pins instants`() {        val zone = ZoneId.of("UTC")
        val day = LocalDate.of(2026, 10, 14)
        val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
        // Three-day conference starting the day before.
        assertTrue(CalendarWeek.coversDate(dayStart - 86_400_000L, dayStart + 2 * 86_400_000L, day, zone))
        assertTrue(CalendarWeek.coversDate(dayStart - 86_400_000L, dayStart + 2 * 86_400_000L, day.plusDays(1), zone))
        assertTrue(!CalendarWeek.coversDate(dayStart - 86_400_000L, dayStart + 2 * 86_400_000L, day.plusDays(2), zone))
        // A normal event does not leak onto neighbours.
        assertTrue(CalendarWeek.coversDate(dayStart + 3_600_000L, dayStart + 7_200_000L, day, zone))
        assertTrue(!CalendarWeek.coversDate(dayStart + 3_600_000L, dayStart + 7_200_000L, day.plusDays(1), zone))
    }

    @Test
    fun `in-progress beats later and ended are gone`() {
        val live = event("Live", now - 1_800_000L, now + 1_800_000L)
        val later = event("Later", now + 3_600_000L, now + 7_200_000L)
        val ended = event("Ended", now - 7_200_000L, now - 3_600_000L)
        assertTrue(UpcomingMerge.isInProgress(live.startMillis, live.endMillis, now))
        assertTrue(!UpcomingMerge.isInProgress(later.startMillis, later.endMillis, now))
        assertTrue(!UpcomingMerge.isInProgress(ended.startMillis, ended.endMillis, now))
        val out = UpcomingMerge.next(listOf(later, ended, live), emptyList(), now, limit = 3)
        assertEquals(listOf("Live", "Later"), out.map { (it as UpcomingItem.Event).event.title })
    }

    @Test
    fun `missing end times are treated conservatively never deleted silently`() {
        // Zero-length instant at now: kept (one-hour grace), not dropped as ended.
        val instant = event("Instant", now, 0L)
        assertEquals(3_600_000L, UpcomingMerge.effectiveEnd(now, 0L) - now)
        val out = UpcomingMerge.next(listOf(instant), emptyList(), now, limit = 3)
        assertEquals(1, out.size)
        // Long past the grace: gone.
        val gone = UpcomingMerge.next(listOf(instant), emptyList(), now + 2 * 3_600_000L, limit = 3)
        assertTrue(gone.isEmpty())
    }

    @Test
    fun `future dates fill short todays and day labels read plainly`() {
        val tomorrowEvent = event("Tomorrow thing", now + 20 * 3_600_000L, now + 21 * 3_600_000L)
        val out = UpcomingMerge.next(listOf(tomorrowEvent), emptyList(), now, limit = 3)
        assertEquals(1, out.size)
        val today = LocalDate.of(2026, 10, 14)
        assertEquals("Today", UpcomingMerge.dayLabel(today, today))
        assertEquals("Tomorrow", UpcomingMerge.dayLabel(today.plusDays(1), today))
        assertEquals(
            today.plusDays(5).format(java.time.format.DateTimeFormatter.ofPattern("d MMM")),
            UpcomingMerge.dayLabel(today.plusDays(5), today),
        )
    }

    @Test
    fun `staleness rule refreshes once per minute of age`() {
        assertTrue(UpcomingMerge.calendarStale(0L, 1_000L))
        assertTrue(UpcomingMerge.calendarStale(1_000L, 61_001L))
        assertTrue(!UpcomingMerge.calendarStale(1_000L, 60_000L))
        assertTrue(!UpcomingMerge.calendarStale(5_000L, 10_000L))
    }
}
