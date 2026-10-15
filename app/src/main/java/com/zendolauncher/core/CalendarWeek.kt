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

    /** True when [startMillis, endMillis) touches [date] — covers multi-day spans. */
    fun coversDate(
        startMillis: Long,
        endMillis: Long,
        date: LocalDate,
        zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
    ): Boolean {
        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        // Zero-length instants belong to the day they start on.
        if (endMillis <= startMillis) {
            return startMillis in dayStart until dayEnd
        }
        return startMillis < dayEnd && endMillis > dayStart
    }
}
