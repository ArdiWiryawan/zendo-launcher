package com.zendolauncher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The access window is arithmetic on minute-of-day, so it is tested as arithmetic:
 * no Robolectric, no device clock, no Compose. Every case is a literal minute, which
 * is the only way the midnight boundary is testable at all.
 *
 * A day is 1440 minutes; 22:00 is 1320, 02:00 is 120, 07:30 is 450, noon is 720.
 */
class AccessWindowTest {

    // --- A window that sits inside one day: 09:00-17:00 -------------------------------

    @Test
    fun `same-day window contains a time after its start and before its end`() {
        assertTrue(AccessWindow.contains(12 * 60, startMinute = 9 * 60, endMinute = 17 * 60))
    }

    @Test
    fun `same-day window excludes a time before its start`() {
        assertFalse(AccessWindow.contains(8 * 60, startMinute = 9 * 60, endMinute = 17 * 60))
    }

    @Test
    fun `same-day window excludes a time after its end`() {
        assertFalse(AccessWindow.contains(18 * 60, startMinute = 9 * 60, endMinute = 17 * 60))
    }

    @Test
    fun `start is inclusive and end is exclusive`() {
        val start = 9 * 60
        val end = 17 * 60
        assertTrue("the start minute itself is inside", AccessWindow.contains(start, start, end))
        assertFalse("the end minute itself is outside", AccessWindow.contains(end, start, end))
    }

    // --- The wrap-around case: 22:00-02:00 -------------------------------------------

    @Test
    fun `wrap-around window contains a time before midnight`() {
        assertTrue(AccessWindow.contains(23 * 60, startMinute = 22 * 60, endMinute = 2 * 60))
    }

    @Test
    fun `wrap-around window contains a time after midnight`() {
        assertTrue(AccessWindow.contains(1 * 60, startMinute = 22 * 60, endMinute = 2 * 60))
    }

    @Test
    fun `wrap-around window contains both of its own endpoints' inner minutes`() {
        val start = 22 * 60
        val end = 2 * 60
        assertTrue(AccessWindow.contains(start, start, end))
        assertFalse(AccessWindow.contains(end, start, end))
        assertTrue(AccessWindow.contains(AccessWindow.MINUTES_PER_DAY - 1, start, end))
        assertTrue(AccessWindow.contains(0, start, end))
    }

    @Test
    fun `wrap-around window excludes the daytime hours it does not cover`() {
        val start = 22 * 60
        val end = 2 * 60
        assertFalse("midnight-adjacent daytime", AccessWindow.contains(2 * 60 + 1, start, end))
        assertFalse("morning", AccessWindow.contains(9 * 60, start, end))
        assertFalse("just before the window opens", AccessWindow.contains(21 * 60 + 59, start, end))
    }

    // --- Degenerate and out-of-range input -------------------------------------------

    @Test
    fun `a zero-length window is empty rather than a whole day`() {
        assertFalse(
            "start == end must not silently disable the gate",
            AccessWindow.contains(12 * 60, startMinute = 12 * 60, endMinute = 12 * 60),
        )
    }

    @Test
    fun `out-of-range minutes wrap into the day instead of throwing`() {
        assertTrue(AccessWindow.contains(12 * 60, startMinute = 9 * 60, endMinute = 17 * 60))
        assertTrue(
            "a stored value past the end of the day is read modulo 1440",
            AccessWindow.contains(12 * 60, startMinute = 9 * 60, endMinute = 17 * 60 + AccessWindow.MINUTES_PER_DAY),
        )
    }

    @Test
    fun `a negative stored start is read modulo the day`() {
        // -60 is 23:00, so 23:30 is inside a 23:00-23:45 window.
        assertTrue(
            AccessWindow.contains(23 * 60 + 30, startMinute = -60, endMinute = 23 * 60 + 45),
        )
    }

    // --- The gate decision ------------------------------------------------------------

    @Test
    fun `a disabled window never withholds a pause the user asked for`() {
        // 03:00, far outside the 22:00-02:00 window: with the window off it is irrelevant.
        assertTrue(
            AccessWindow.shouldPause(
                pauseEnabled = true,
                inDistract = true,
                windowEnabled = false,
                nowMinute = 3 * 60,
                startMinute = 22 * 60,
                endMinute = 2 * 60,
            ),
        )
    }

    @Test
    fun `enabled window inside passes through without pausing`() {
        assertFalse(
            AccessWindow.shouldPause(
                pauseEnabled = true,
                inDistract = true,
                windowEnabled = true,
                nowMinute = 23 * 60,
                startMinute = 22 * 60,
                endMinute = 2 * 60,
            ),
        )
    }

    @Test
    fun `enabled window outside pauses`() {
        assertTrue(
            AccessWindow.shouldPause(
                pauseEnabled = true,
                inDistract = true,
                windowEnabled = true,
                nowMinute = 9 * 60,
                startMinute = 22 * 60,
                endMinute = 2 * 60,
            ),
        )
    }

    @Test
    fun `the window can never start gating an app the user did not list or a pause that is off`() {
        assertFalse(
            "a listed app with the pause off is untouched",
            AccessWindow.shouldPause(
                pauseEnabled = false,
                inDistract = true,
                windowEnabled = true,
                nowMinute = 12 * 60,
                startMinute = 22 * 60,
                endMinute = 2 * 60,
            ),
        )
        assertFalse(
            "an unlisted app is untouched however the window is set",
            AccessWindow.shouldPause(
                pauseEnabled = true,
                inDistract = false,
                windowEnabled = true,
                nowMinute = 12 * 60,
                startMinute = 22 * 60,
                endMinute = 2 * 60,
            ),
        )
    }

    // --- Readout formatting -----------------------------------------------------------

    @Test
    fun `formats a minute of day as 24-hour clock text`() {
        assertEquals("00:00", AccessWindow.format(0))
        assertEquals("02:00", AccessWindow.format(2 * 60))
        assertEquals("22:00", AccessWindow.format(22 * 60))
        assertEquals("23:45", AccessWindow.format(23 * 60 + 45))
    }

    @Test
    fun `format wraps rather than printing an impossible time`() {
        assertEquals("00:00", AccessWindow.format(AccessWindow.MINUTES_PER_DAY))
        assertEquals("00:00", AccessWindow.format(-AccessWindow.MINUTES_PER_DAY))
    }
}
