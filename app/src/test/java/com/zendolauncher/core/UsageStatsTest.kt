package com.zendolauncher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageStatsTest {

    private val midnight = 1_000_000L

    @Test
    fun `aggregates sessions and launch counts for multiple packages`() {
        val events = listOf(
            RawUsageEvent("com.ig", UsageEventType.FOREGROUND, midnight + 10_000L),
            RawUsageEvent("com.ig", UsageEventType.BACKGROUND, midnight + 70_000L), // 1m
            RawUsageEvent("com.notes", UsageEventType.FOREGROUND, midnight + 100_000L),
            RawUsageEvent("com.notes", UsageEventType.BACKGROUND, midnight + 220_000L), // 2m
            RawUsageEvent("com.ig", UsageEventType.FOREGROUND, midnight + 300_000L),
            RawUsageEvent("com.ig", UsageEventType.BACKGROUND, midnight + 420_000L), // 2m
        )

        val snapshot = UsageCalculator.aggregate(events, midnight, midnight + 500_000L)
        assertEquals(3, snapshot.totalLaunches)
        assertEquals(300_000L, snapshot.totalForegroundMillis)

        val ig = snapshot.byPackage.getValue("com.ig")
        assertEquals(180_000L, ig.foregroundMillis)
        assertEquals(2, ig.launchCount)

        val notes = snapshot.byPackage.getValue("com.notes")
        assertEquals(120_000L, notes.foregroundMillis)
        assertEquals(1, notes.launchCount)
    }

    @Test
    fun `clamps open foreground session at nowMillis`() {
        val events = listOf(
            RawUsageEvent("com.ig", UsageEventType.FOREGROUND, midnight + 60_000L),
        )
        val snapshot = UsageCalculator.aggregate(events, midnight, midnight + 180_000L)
        assertEquals(120_000L, snapshot.byPackage.getValue("com.ig").foregroundMillis)
        assertEquals(1, snapshot.byPackage.getValue("com.ig").launchCount)
    }

    @Test
    fun `ignores orphan leading background events and excluded packages`() {
        val events = listOf(
            RawUsageEvent("com.ig", UsageEventType.BACKGROUND, midnight + 30_000L),
            RawUsageEvent("com.zendolauncher", UsageEventType.FOREGROUND, midnight + 40_000L),
            RawUsageEvent("com.zendolauncher", UsageEventType.BACKGROUND, midnight + 90_000L),
        )
        val snapshot = UsageCalculator.aggregate(
            events = events,
            dayStartMillis = midnight,
            nowMillis = midnight + 120_000L,
            excludedPackages = setOf("com.zendolauncher"),
        )
        assertTrue(snapshot.byPackage.isEmpty())
        assertEquals(0L, snapshot.totalForegroundMillis)
        assertEquals(0, snapshot.totalLaunches)
    }

    @Test
    fun `formats durations and mirror lines cleanly`() {
        assertEquals("0m", UsageCalculator.formatDuration(0L))
        assertEquals("<1m", UsageCalculator.formatDuration(25_000L))
        assertEquals("42m", UsageCalculator.formatDuration(42 * 60_000L))
        assertEquals("1h", UsageCalculator.formatDuration(60 * 60_000L))
        assertEquals("1h 12m", UsageCalculator.formatDuration(72 * 60_000L))

        assertEquals("Today: not opened yet", UsageCalculator.formatCompactMirror(null))
        assertEquals(
            "Today: 38m · opened 9×",
            UsageCalculator.formatCompactMirror(AppUsageSummary("com.ig", 38 * 60_000L, 9)),
        )
    }
}
