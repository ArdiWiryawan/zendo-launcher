package com.zendolauncher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskItemTest {

    private fun t(id: String, title: String, done: Boolean = false, show: Boolean = true, created: Long = 0) =
        TaskItem(id = id, title = title, done = done, showOnHome = show, createdAt = created)

    @Test
    fun `blank titles dropped and undone first`() {
        val out = TaskItem.sanitizeAll(
            listOf(t("a", "  "), t("b", "Done", done = true, created = 2), t("c", "Todo", created = 1)),
        )
        assertEquals(listOf("c", "b"), out.map { it.id })
    }

    @Test
    fun `home summary respects limit and showOnHome`() {
        val items = listOf(
            t("a", "A", created = 1),
            t("b", "B", show = false, created = 2),
            t("c", "C", created = 3),
            t("d", "D", created = 4),
        )
        assertEquals(listOf("a", "c"), items.homeSummary(2).map { it.id })
    }

    @Test
    fun `done count counts done`() {
        val items = listOf(t("a", "A", done = true), t("b", "B"))
        assertEquals(1, items.doneCount())
        assertEquals(2, items.visibleCount())
    }

    @Test
    fun `views split today upcoming all and done without a new model`() {
        val zone = java.time.ZoneId.of("UTC")
        val today = java.time.LocalDate.of(2026, 10, 14)
        val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val items = listOf(
            TaskItem(id = "over", title = "Over", dueDateMillis = dayStart - 3_600_000L),
            TaskItem(id = "today", title = "Today", dueDateMillis = dayStart + 3_600_000L),
            TaskItem(id = "future", title = "Future", dueDateMillis = dayStart + 3 * 86_400_000L),
            TaskItem(id = "undated", title = "Undated"),
            TaskItem(id = "done", title = "Done", done = true, dueDateMillis = dayStart + 3_600_000L),
        )
        assertEquals(
            listOf("over", "today"),
            items.forView(TaskView.TODAY, today, zone).map { it.id },
        )
        assertEquals(listOf("future"), items.forView(TaskView.UPCOMING, today, zone).map { it.id })
        assertEquals(5, items.forView(TaskView.ALL, today, zone).size)
        assertEquals(listOf("done"), items.forView(TaskView.DONE, today, zone).map { it.id })
    }

    @Test
    fun `overdue reads as a quiet word with day counts`() {
        val zone = java.time.ZoneId.of("UTC")
        val today = java.time.LocalDate.of(2026, 10, 14)
        val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val yesterday = TaskItem(id = "y", title = "Y", dueDateMillis = dayStart - 3_600_000L)
        assertTrue(yesterday.isOverdue(today, zone))
        assertEquals("Overdue", yesterday.overdueLabel(today, zone))
        val old = TaskItem(id = "o", title = "O", dueDateMillis = dayStart - 3 * 86_400_000L)
        assertEquals("Overdue · 4d", old.overdueLabel(today, zone))
        val current = TaskItem(id = "c", title = "C", dueDateMillis = dayStart + 3_600_000L)
        assertEquals(null, current.overdueLabel(today, zone))
        assertEquals(null, TaskItem(id = "u", title = "U").overdueLabel(today, zone))
        assertEquals(
            null,
            TaskItem(id = "d", title = "D", done = true, dueDateMillis = dayStart - 3_600_000L)
                .overdueLabel(today, zone),
        )
    }
}
