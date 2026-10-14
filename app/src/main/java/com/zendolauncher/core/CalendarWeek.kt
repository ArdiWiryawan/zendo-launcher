package com.zendolauncher.core

import java.time.LocalDate

/**
 * Week strip helpers. Monday-first (M T W T F S S), matching the design.
 * Pure so it is unit-testable without a device calendar.
 */
object CalendarWeek {

    fun weekFor(date: LocalDate): List<LocalDate> {
        // Monday = 1 .. Sunday = 7
        val dow = date.dayOfWeek.value
        val monday = date.minusDays((dow - 1).toLong())
        return (0..6).map { monday.plusDays(it.toLong()) }
    }

    fun isSameDay(aMillis: Long, bMillis: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Boolean {
        val a = java.time.Instant.ofEpochMilli(aMillis).atZone(zone).toLocalDate()
        val b = java.time.Instant.ofEpochMilli(bMillis).atZone(zone).toLocalDate()
        return a == b
    }
}
