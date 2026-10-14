package com.zendolauncher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightTest {

    private fun h(id: String, title: String, primary: Boolean = false, archived: Boolean = false) =
        Highlight(id = id, title = title, isPrimary = primary, archived = archived)

    @Test
    fun `blank titles are dropped`() {
        val out = Highlight.sanitizeAll(listOf(h("a", "  "), h("b", "Keep")))
        assertEquals(listOf("b"), out.map { it.id })
    }

    @Test
    fun `exactly one primary among visible`() {
        val out = Highlight.sanitizeAll(
            listOf(h("a", "A", primary = true), h("b", "B", primary = true)),
        )
        assertEquals(1, out.count { it.isPrimary })
    }

    @Test
    fun `first visible becomes primary when none pinned`() {
        val out = Highlight.sanitizeAll(listOf(h("a", "A"), h("b", "B")))
        assertEquals("a", out.primary()?.id)
    }

    @Test
    fun `archived are excluded from primary and visible`() {
        val out = Highlight.sanitizeAll(
            listOf(h("a", "A", archived = true), h("b", "B")),
        )
        assertEquals(listOf("B"), out.visible().map { it.title })
        assertEquals(1, out.archivedList().size)
        assertTrue(out.primary()?.id == "b")
    }

    @Test
    fun `duplicate ids are dropped`() {
        val out = Highlight.sanitizeAll(listOf(h("a", "A"), h("a", "B")))
        assertEquals(1, out.size)
    }
}
