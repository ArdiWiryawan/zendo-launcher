package com.zendolauncher.ui

/**
 * The optional daily access window, as pure arithmetic on minute-of-day.
 *
 * Kept out of [LauncherViewModel] so the wrap-around case is testable without a device,
 * a clock, or Robolectric. The window is a local wall-clock interval with no timezone
 * or DST handling beyond what `java.time.LocalTime` already applies: the launcher asks
 * the device what time it is, it does not decide what time it should be.
 */
object AccessWindow {

    /** Minutes in a day; the modulus every window comparison wraps around. */
    const val MINUTES_PER_DAY = 24 * 60

    /**
     * True when [minuteOfDay] falls inside the half-open window [startMinute, endMinute).
     *
     * Two cases, and the wrap-around one is why this is a function rather than a pair of
     * comparisons at the call site:
     *  - start <= end: the window sits inside one day, so `start <= now < end`.
     *  - start > end:  the window crosses midnight (22:00–02:00), so it is the union of
     *    `now >= start` and `now < end` — the complement of the same-day gap.
     *
     * A zero-length window (start == end) is empty, not a whole day. "22:00 to 22:00"
     * reads as no time at all, and treating it as always-open would silently disable the
     * gate the user just turned on.
     *
     * Every input is taken modulo the day rather than rejected: the values come from
     * SharedPreferences, and a corrupt entry should still produce a usable answer instead
     * of crashing the launcher on a tap. The setters clamp what they store, so the
     * wrapping here is the second line of defence, not the first.
     */
    fun contains(minuteOfDay: Int, startMinute: Int, endMinute: Int): Boolean {
        val now = Math.floorMod(minuteOfDay, MINUTES_PER_DAY)
        val start = Math.floorMod(startMinute, MINUTES_PER_DAY)
        val end = Math.floorMod(endMinute, MINUTES_PER_DAY)
        if (start == end) return false
        return if (start < end) {
            now >= start && now < end
        } else {
            now >= start || now < end
        }
    }

    /**
     * Formats a minute-of-day as 24-hour `HH:mm` for the settings rows.
     *
     * Built by hand rather than with `String.format`, whose `%d` honours the default
     * locale and would print non-ASCII digits on some devices — a readout the user has
     * to match against a stored number should not change script with the locale.
     */
    fun format(minuteOfDay: Int): String {
        val m = Math.floorMod(minuteOfDay, MINUTES_PER_DAY)
        val hour = (m / 60).toString().padStart(2, '0')
        val minute = (m % 60).toString().padStart(2, '0')
        return "$hour:$minute"
    }

    /**
     * The pause gate's whole decision, as one expression.
     *
     * The window can only ever *withhold* a pause the user already asked for: with the
     * pause off, or the app not on the distraction list, nothing here pauses it. That
     * direction matters — an enabled window must never become a second, hidden switch
     * that starts gating apps the user never listed.
     */
    fun shouldPause(
        pauseEnabled: Boolean,
        inDistract: Boolean,
        windowEnabled: Boolean,
        nowMinute: Int,
        startMinute: Int,
        endMinute: Int,
    ): Boolean {
        if (!pauseEnabled || !inDistract) return false
        if (!windowEnabled) return true
        return !contains(nowMinute, startMinute, endMinute)
    }
}
