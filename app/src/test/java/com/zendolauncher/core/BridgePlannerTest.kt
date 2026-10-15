package com.zendolauncher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * The planner is the single decision point for a bridge import: the preview
 * dialog and the apply step read the same [BridgePlan]. These tests pin the
 * rules that make imports safe: fill blanks, refresh what a previous import
 * wrote, never touch local text, never duplicate by stable id.
 */
class BridgePlannerTest {

    private val today = LocalDate.of(2026, 10, 15)
    private val zone = ZoneId.of("Asia/Jakarta")
    private val now = 1_790_000_000_000L

    private fun snapshot(
        date: LocalDate = today,
        action: String? = "Ship the deck",
        highlight: String? = "Slow morning walk",
        tasks: List<BridgeTask> = listOf(BridgeTask(id = "p1", title = "Draft outline", sortOrder = 0)),
    ) = BridgeSnapshot(
        source = "zendo-pwa",
        exportedAt = "2026-10-15T08:00:00+07:00",
        localDate = date,
        action = action?.let { BridgeAction(id = "a1", title = it) },
        highlight = highlight?.let { BridgeHighlight(id = "h1", text = it) },
        tasks = tasks,
    )

    @Test
    fun `merge into an empty day fills the action, highlight and tasks`() {
        val plan = BridgePlanner.plan(snapshot(), today, ImportMode.MERGE, zone, now)

        assertEquals("Ship the deck", plan.actionTitle)
        val added = plan.tasks!!.filter { it.sourceId == "p1" }
        assertEquals(1, added.size)
        assertEquals(Origins.ZENDO_PWA, added.single().origin)
        assertEquals(now, added.single().createdAt)

        val highlight = plan.highlights!!.single()
        assertEquals("Slow morning walk", highlight.title)
        assertTrue(highlight.isPrimary)
        assertEquals(Origins.ZENDO_PWA, highlight.origin)
        assertEquals("h1", highlight.sourceId)
    }

    @Test
    fun `re-importing the same snapshot changes nothing`() {
        val first = BridgePlanner.plan(snapshot(), today, ImportMode.MERGE, zone, now)
        val second = BridgePlanner.plan(
            snapshot(),
            today,
            ImportMode.MERGE,
            zone,
            now + 1_000,
            currentActionTitle = "Ship the deck",
            currentActionOrigin = Origins.ZENDO_PWA,
            currentHighlights = first.highlights!!,
            currentTasks = first.tasks!!,
        )

        assertNull(second.actionTitle)
        assertNull(second.highlights)
        assertNull(second.tasks)
        assertFalse(second.summary.hasAnyChange)
    }

    @Test
    fun `local text is never overwritten in merge mode`() {
        val plan = BridgePlanner.plan(
            snapshot(),
            today,
            ImportMode.MERGE,
            zone,
            now,
            currentActionTitle = "My own action",
            currentHighlights = listOf(Highlight(id = "h9", title = "My highlight", isPrimary = true)),
            currentTasks = listOf(TaskItem(id = "t1", title = "Local task")),
        )

        assertNull(plan.actionTitle)
        assertNull(plan.highlights)
        val tasks = plan.tasks!!
        assertEquals(2, tasks.size)
        assertTrue(tasks.any { it.id == "t1" })
        assertTrue(tasks.any { it.sourceId == "p1" })
    }

    @Test
    fun `an action a previous import wrote is refreshed`() {
        val plan = BridgePlanner.plan(
            snapshot(action = "Ship the deck v2"),
            today,
            ImportMode.MERGE,
            zone,
            now,
            currentActionTitle = "Ship the deck",
            currentActionOrigin = Origins.ZENDO_PWA,
        )
        assertEquals("Ship the deck v2", plan.actionTitle)
    }

    @Test
    fun `replace mode removes previous imports and keeps local records`() {
        val previous = listOf(
            TaskItem(id = "t1", title = "Old import", origin = Origins.ZENDO_PWA, sourceId = "p9"),
            TaskItem(id = "t2", title = "Local"),
        )
        val plan = BridgePlanner.plan(
            snapshot(),
            today,
            ImportMode.REPLACE_IMPORTED,
            zone,
            now,
            currentTasks = previous,
        )

        assertEquals(1, plan.summary.removedImportedTasks)
        val tasks = plan.tasks!!
        assertFalse(tasks.any { it.id == "t1" })
        assertTrue(tasks.any { it.id == "t2" })
        assertEquals(1, tasks.count { it.sourceId == "p1" })
    }

    @Test
    fun `a snapshot for a past date lands only in that day's record`() {
        val yesterday = today.minusDays(1)
        val plan = BridgePlanner.plan(snapshot(date = yesterday), today, ImportMode.MERGE, zone, now)

        assertFalse(plan.isToday)
        assertNull(plan.tasks)
        assertNull(plan.actionTitle)
        val record = plan.dayRecord!!
        assertEquals(yesterday, record.date)
        assertEquals(DayRecord.idFor(yesterday), record.id)
        assertEquals("Ship the deck", record.actionTitle)
        val task = record.tasks.single()
        assertEquals(Origins.ZENDO_PWA, task.origin)
        assertEquals("p1", task.sourceId)
    }

    @Test
    fun `a past merge keeps that day's local content`() {
        val yesterday = today.minusDays(1)
        val existing = DayRecord(
            id = DayRecord.idFor(yesterday),
            date = yesterday,
            actionTitle = "Kept locally",
            tasks = listOf(
                DayTaskRecord(id = "dt_local", title = "Local", done = true, createdAt = 1L, order = 0),
            ),
        )
        val plan = BridgePlanner.plan(
            snapshot(date = yesterday),
            today,
            ImportMode.MERGE,
            zone,
            now,
            currentDay = existing,
        )

        val record = plan.dayRecord!!
        assertEquals("Kept locally", record.actionTitle)
        assertEquals("Slow morning walk", record.highlightText)
        assertEquals(2, record.tasks.size)
        assertTrue(record.tasks.any { it.id == "dt_local" })
        assertEquals(listOf(0, 1), record.tasks.map { it.order })
    }

    @Test
    fun `a past replace removes only imported records`() {
        val yesterday = today.minusDays(1)
        val existing = DayRecord(
            id = DayRecord.idFor(yesterday),
            date = yesterday,
            tasks = listOf(
                DayTaskRecord(
                    id = "dt_old",
                    title = "Old import",
                    done = false,
                    createdAt = 1L,
                    order = 0,
                    origin = Origins.ZENDO_PWA,
                    sourceId = "p9",
                ),
                DayTaskRecord(id = "dt_local", title = "Local", done = false, createdAt = 2L, order = 1),
            ),
        )
        val plan = BridgePlanner.plan(
            snapshot(date = yesterday),
            today,
            ImportMode.REPLACE_IMPORTED,
            zone,
            now,
            currentDay = existing,
        )

        assertEquals(1, plan.summary.removedImportedTasks)
        val tasks = plan.dayRecord!!.tasks
        assertFalse(tasks.any { it.id == "dt_old" })
        assertTrue(tasks.any { it.id == "dt_local" })
        assertTrue(tasks.any { it.sourceId == "p1" })
    }

    @Test
    fun `an empty snapshot changes nothing anywhere`() {
        val empty = BridgeSnapshot(
            source = "zendo-pwa",
            exportedAt = "2026-10-15T08:00:00+07:00",
            localDate = today,
        )
        val plan = BridgePlanner.plan(empty, today, ImportMode.MERGE, zone, now)
        assertFalse(plan.summary.hasAnyChange)
        assertNull(plan.actionTitle)
        assertNull(plan.tasks)
        assertNull(plan.highlights)

        val past = BridgePlanner.plan(
            empty.copy(localDate = today.minusDays(1)),
            today,
            ImportMode.MERGE,
            zone,
            now,
        )
        assertNull(past.dayRecord)
    }
}
