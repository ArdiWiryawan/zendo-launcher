package com.zendolauncher.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zendolauncher.core.DayRecord
import com.zendolauncher.core.DayTaskRecord
import com.zendolauncher.core.Origins
import com.zendolauncher.core.TaskItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class HistoryStoreTest {

    private fun store() = HistoryStore(ApplicationProvider.getApplicationContext())

    private val day1: LocalDate = LocalDate.of(2026, 10, 13)
    private val day2: LocalDate = LocalDate.of(2026, 10, 14)

    private fun task(title: String = "Draft outline", done: Boolean = false) =
        TaskItem(id = "t1", title = title, done = done, createdAt = 100L, updatedAt = 100L)

    @Test
    fun `capture writes today and follows the live data`() {
        val store = store()
        assertTrue(store.capture(day1, "Ship the deck", false, "Walk", listOf(task())))
        val record = store.recordFor(day1)
        assertNotNull(record)
        assertEquals("Ship the deck", record!!.actionTitle)
        assertEquals("Walk", record.highlightText)
        assertEquals(1, record.tasks.size)

        // Identical content is a no-op: no timestamp churn, no writes.
        assertFalse(store.capture(day1, "Ship the deck", false, "Walk", listOf(task())))

        // A real change writes.
        assertTrue(store.capture(day1, "Ship the deck", false, "Walk", listOf(task(done = true))))
        assertTrue(store.recordFor(day1)!!.tasks.single().done)
    }

    @Test
    fun `an earlier day keeps the state it was last written with`() {
        val store = store()
        store.capture(day1, "Ship the deck", false, "Walk", listOf(task(done = false)))
        // The same task is completed the next day; history must not follow.
        store.capture(day2, "Ship the deck", false, "Walk", listOf(task(done = true)))

        assertFalse(store.recordFor(day1)!!.tasks.single().done)
        assertTrue(store.recordFor(day2)!!.tasks.single().done)
        assertTrue(store.recordFor(day1)!!.doneCount == 0)
    }

    @Test
    fun `upsert replaces the record for a date and clear empties everything`() {
        val store = store()
        store.capture(day1, "One", false, "", emptyList())
        store.capture(day2, "Two", false, "", emptyList())
        store.upsert(DayRecord(id = "ignored", date = day1, actionTitle = "Replaced", updatedAt = 999L))
        assertEquals("Replaced", store.recordFor(day1)!!.actionTitle)
        assertEquals(2, store.days.value.size)

        store.clear()
        assertTrue(store.days.value.isEmpty())
        assertNull(store.recordFor(day1))
    }

    @Test
    fun `records survive an encode and decode with order and origin intact`() {
        val days = listOf(
            DayRecord(
                id = DayRecord.idFor(day2),
                date = day2,
                actionTitle = "Ship the deck",
                actionDone = true,
                highlightText = "Walk",
                tasks = listOf(
                    DayTaskRecord(id = "t1", title = "First", done = false, createdAt = 1L, order = 0),
                    DayTaskRecord(
                        id = "dt_p9",
                        title = "Imported",
                        done = true,
                        createdAt = 2L,
                        order = 1,
                        origin = Origins.ZENDO_PWA,
                        sourceId = "p9",
                    ),
                ),
                origin = Origins.ZENDO_PWA,
                updatedAt = 123L,
            ),
        )
        val decoded = HistoryStore.decode(HistoryStore.encode(days)).single()
        assertEquals(day2, decoded.date)
        assertEquals(DayRecord.idFor(day2), decoded.id)
        assertTrue(decoded.actionDone)
        assertEquals(Origins.ZENDO_PWA, decoded.origin)
        assertEquals(listOf("t1", "dt_p9"), decoded.tasks.map { it.id })
        assertEquals(listOf(0, 1), decoded.tasks.map { it.order })
        assertEquals(Origins.ZENDO_PWA, decoded.tasks[1].origin)
        assertEquals("p9", decoded.tasks[1].sourceId)
    }

    @Test
    fun `a bare array from an older file still reads`() {
        val raw = """[{"id":"d_2026-10-13","date":"2026-10-13","actionTitle":"Legacy","updatedAt":5}]"""
        val decoded = HistoryStore.decode(raw).single()
        assertEquals(day1, decoded.date)
        assertEquals("Legacy", decoded.actionTitle)
    }

    @Test
    fun `malformed history reads as empty instead of crashing`() {
        assertTrue(HistoryStore.decode(null).isEmpty())
        assertTrue(HistoryStore.decode("broken").isEmpty())
        assertTrue(HistoryStore.decode("""{"version":1}""").isEmpty())
    }

    @Test
    fun `history keeps the newest days and drops the oldest beyond the cap`() {
        val start = LocalDate.of(2025, 1, 1)
        val records = (0 until DayRecord.MAX_DAYS + 5).map { offset ->
            DayRecord(
                id = "d_$offset",
                date = start.plusDays(offset.toLong()),
                actionTitle = "Day $offset",
                updatedAt = offset.toLong(),
            )
        }
        val sanitized = DayRecord.sanitizeAll(records)
        assertEquals(DayRecord.MAX_DAYS, sanitized.size)
        assertEquals(start.plusDays((DayRecord.MAX_DAYS + 4).toLong()), sanitized.first().date)
        assertFalse(sanitized.any { it.date == start })
    }
}
