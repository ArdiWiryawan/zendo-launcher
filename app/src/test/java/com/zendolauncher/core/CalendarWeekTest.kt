package com.zendolauncher.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CalendarWeekTest {

    @Test
    fun `weekFor is Monday-first with 7 days containing the date`() {
        // 2026-10-14 is a Wednesday.
        val week = CalendarWeek.weekFor(LocalDate.of(2026, 10, 14))
        assertEquals(7, week.size)
        assertEquals(LocalDate.of(2026, 10, 12), week.first())
        assertEquals(LocalDate.of(2026, 10, 18), week.last())
    }

    @Test
    fun `weekFor on Monday starts that day`() {
        val week = CalendarWeek.weekFor(LocalDate.of(2026, 10, 12))
        assertEquals(LocalDate.of(2026, 10, 12), week.first())
    }

    @Test
    fun `weekFor on Sunday ends that day`() {
        val week = CalendarWeek.weekFor(LocalDate.of(2026, 10, 18))
        assertEquals(LocalDate.of(2026, 10, 18), week.last())
    }
}
