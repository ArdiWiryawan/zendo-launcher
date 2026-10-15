package com.zendolauncher.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarCacheTest {

    @Test
    fun `never-loaded cache is always stale`() {
        assertTrue(CalendarCache.isStale(0L, 1_000L))
    }

    @Test
    fun `fresh cache is not stale, old cache is`() {
        assertTrue(CalendarCache.isStale(1_000L, 61_001L))
        assertFalse(CalendarCache.isStale(1_000L, 60_000L))
        assertFalse(CalendarCache.isStale(5_000L, 10_000L))
    }

    @Test
    fun `custom threshold is honoured`() {
        assertTrue(CalendarCache.isStale(1_000L, 3_001L, staleAfterMillis = 2_000L))
        assertFalse(CalendarCache.isStale(1_000L, 3_000L, staleAfterMillis = 2_000L))
    }
}
