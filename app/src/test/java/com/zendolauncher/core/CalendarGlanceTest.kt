package com.zendolauncher.core

import com.zendolauncher.model.CalendarEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class CalendarGlanceTest {

    private val zone = ZoneOffset.UTC
    private val dayStart = LocalDate.of(2026, 11, 19).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun event(
        id: Long,
        title: String,
        startHour: Int,
        startMin: Int,
        endHour: Int,
        endMin: Int,
        allDay: Boolean = false,
    ): CalendarEvent = CalendarEvent(
        id = id,
        title = title,
        startMillis = dayStart + (startHour * 60L + startMin) * 60_000L,
        endMillis = dayStart + (endHour * 60L + endMin) * 60_000L,
        allDay = allDay,
        location = null,
    )

    @Test
    fun `empty events returns open day`() {
        val summary = CalendarGlance.summarize(emptyList(), dayStart + 10 * 3_600_000L, zone)
        assertEquals("Open day · nothing scheduled", summary.headline)
        assertNull(summary.subline)
    }

    @Test
    fun `in progress event reports end time and next event`() {
        val events = listOf(
            event(1, "Team Sync", 10, 0, 11, 0),
            event(2, "Deep Work", 13, 0, 14, 30),
        )
        val now = dayStart + 10 * 3_600_000L + 15 * 60_000L
        val summary = CalendarGlance.summarize(events, now, zone)
        assertEquals("Now · Team Sync (until 11:00)", summary.headline)
        assertEquals("Next 13:00 · Deep Work", summary.subline)
    }

    @Test
    fun `upcoming event shows countdown and remaining count`() {
        val events = listOf(
            event(1, "Deep Work", 10, 30, 12, 0),
            event(2, "Dinner", 18, 0, 19, 0),
        )
        val now = dayStart + 10 * 3_600_000L
        val summary = CalendarGlance.summarize(events, now, zone)
        assertEquals("10:30 · Deep Work (in 30m)", summary.headline)
        assertEquals("+1 more later today", summary.subline)
    }

    @Test
    fun `all day event shows when no timed events remain`() {
        val events = listOf(
            CalendarEvent(
                id = 1L,
                title = "Focus Day",
                startMillis = dayStart,
                endMillis = dayStart + 86_400_000L,
                allDay = true,
                location = null,
            ),
        )
        val now = dayStart + 9 * 3_600_000L
        val summary = CalendarGlance.summarize(events, now, zone)
        assertEquals("Today · Focus Day", summary.headline)
        assertNull(summary.subline)
    }
}
