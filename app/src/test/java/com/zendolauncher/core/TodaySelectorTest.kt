package com.zendolauncher.core

import com.zendolauncher.model.ZendoToday
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TodaySelectorTest {

    private val today = LocalDate.of(2026, 10, 13)

    @Test
    fun `returns null when there is no plan for today`() {
        assertNull(TodaySelector.select(emptyList(), today))
        assertNull(TodaySelector.select(listOf(ZendoDayPlan(date = "2026-10-12", highlight = "Yesterday")), today))
    }

    @Test
    fun `maps highlight main action and first agenda item`() {
        val result = TodaySelector.select(
            listOf(
                ZendoDayPlan(
                    date = "2026-10-13",
                    highlight = "Finish the report",
                    mainAction = "Draft section 2",
                    agenda = listOf("Learning", "Review"),
                ),
            ),
            today,
        )
        assertEquals("Finish the report", result?.highlight)
        assertEquals("Draft section 2", result?.mainAction)
        assertEquals("Learning", result?.nextAgenda?.title)
        assertNull("Zendo agenda items carry no time", result?.nextAgenda?.time)
        assertEquals(emptyList<String>(), result?.practices)
    }

    @Test
    fun `ignores blank fields and skips blank agenda entries`() {
        val result = TodaySelector.select(
            listOf(ZendoDayPlan(date = "2026-10-13", highlight = "  ", mainAction = "Ship it", agenda = listOf("", "Learning"))),
            today,
        )
        assertNull(result?.highlight)
        assertEquals("Ship it", result?.mainAction)
        assertEquals("Learning", result?.nextAgenda?.title)
    }

    @Test
    fun `an empty plan for today renders nothing`() {
        assertNull(TodaySelector.select(listOf(ZendoDayPlan(date = "2026-10-13")), today))
    }

    @Test
    fun `uses the last entry when a date appears twice`() {
        val result = TodaySelector.select(
            listOf(
                ZendoDayPlan(date = "2026-10-13", highlight = "Older"),
                ZendoDayPlan(date = "2026-10-13", highlight = "Newer"),
            ),
            today,
        )
        assertEquals("Newer", result?.highlight)
    }

    @Test
    fun `agenda item with a time is preserved when supplied`() {
        val result = TodaySelector.select(
            listOf(ZendoDayPlan(date = "2026-10-13", agenda = listOf("Learning · 16:00"))),
            today,
        )
        assertEquals("Learning · 16:00", result?.nextAgenda?.title)
    }
}
