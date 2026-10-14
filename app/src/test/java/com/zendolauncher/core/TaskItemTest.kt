package com.zendolauncher.core

import org.junit.Assert.assertEquals
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
}
