package com.zendolauncher.core

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageFormatTest {

    @Test
    fun `formats minutes and hours`() {
        assertEquals("0 min", UsageFormat.minutes(0))
        assertEquals("5 min", UsageFormat.minutes(5))
        assertEquals("45 min", UsageFormat.minutes(45))
        assertEquals("1 h", UsageFormat.minutes(60))
        assertEquals("1 h 30 min", UsageFormat.minutes(90))
        assertEquals("2 h 5 min", UsageFormat.minutes(125))
    }

    @Test
    fun `clamps negatives instead of printing them`() {
        assertEquals("0 min", UsageFormat.minutes(-3))
    }
}
