package com.zendolauncher.core

/**
 * Readouts for the optional local usage summary. Pure so the wording is
 * unit-tested without the usage-stats service.
 */
object UsageFormat {

    /** 0 → "0 min", 45 → "45 min", 60 → "1 h", 90 → "1 h 30 min". */
    fun minutes(totalMinutes: Int): String {
        val m = totalMinutes.coerceAtLeast(0)
        if (m < 60) return "$m min"
        val h = m / 60
        val rest = m % 60
        return if (rest == 0) "$h h" else "$h h $rest min"
    }
}
