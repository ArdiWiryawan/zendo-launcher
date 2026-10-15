package com.zendolauncher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The repair rules, tested as rules. [HomeLayout.sanitize] runs on every read and after
 * every mutation, which is what lets every consumer trust a layout without checking it —
 * so the cases here are the states a bad write, a hand-edited file or a downgrade could
 * leave behind. No Android, no Robolectric: the model is pure Kotlin on purpose.
 */
class HomeLayoutSanitizeTest {

    @Test
    fun `an empty layout becomes the default`() {
        assertEquals(HomeLayout.DEFAULT, HomeLayout.sanitize(HomeLayout(emptyList())))
    }

    @Test
    fun `a blank id is replaced and the pages stay in order`() {
        val layout = HomeLayout(
            listOf(
                HomePage("", PageType.APPS, "First"),
                HomePage("p2", PageType.APPS, "Second"),
            ),
        )
        val fixed = HomeLayout.sanitize(layout)
        assertTrue(fixed.pages[0].id.isNotBlank())
        assertEquals(listOf("First", "Second"), fixed.pages.map { it.name })
    }

    @Test
    fun `a duplicate id is replaced rather than merged`() {
        // Two pages sharing an id would make every id-addressed operation act on both.
        val layout = HomeLayout(
            listOf(
                HomePage("dup", PageType.APPS, "One"),
                HomePage("dup", PageType.APPS, "Two"),
            ),
        )
        val fixed = HomeLayout.sanitize(layout)
        assertEquals(2, fixed.pages.map { it.id }.distinct().size)
        assertEquals("dup", fixed.pages[0].id)
    }

    @Test
    fun `a blank name falls back to the type's name so a page is never nameless`() {
        val layout = HomeLayout(listOf(HomePage("p1", PageType.TODAY, "  ")))
        assertEquals(PageType.TODAY.defaultName(), HomeLayout.sanitize(layout).pages.single().name)
    }

    @Test
    fun `more than five pages is truncated, keeping the first five`() {
        val layout = HomeLayout((0 until 9).map { HomePage("p$it", PageType.APPS, "Page $it") })
        val fixed = HomeLayout.sanitize(layout)
        assertEquals(HomeLayout.MAX_PAGES, fixed.pages.size)
        assertEquals(listOf("p0", "p1", "p2", "p3", "p4"), fixed.pages.map { it.id })
    }

    @Test
    fun `a single page is allowed because removing the last one is refused elsewhere`() {
        assertEquals(1, HomeLayout.sanitize(HomeLayout(listOf(HomePage("p1", PageType.APPS, "A")))).pages.size)
    }

    @Test
    fun `an active id that resolves to nothing falls back to the first page`() {
        val layout = HomeLayout(listOf(HomePage("p1", PageType.APPS, "A")), activePageId = "gone")
        assertEquals("p1", HomeLayout.sanitize(layout).activePageId)
    }

    @Test
    fun `a null active id falls back to the first page`() {
        val layout = HomeLayout(listOf(HomePage("p1", PageType.APPS, "A")), activePageId = null)
        assertEquals("p1", HomeLayout.sanitize(layout).activePageId)
    }

    @Test
    fun `item keys are de-duplicated and blanks dropped, keeping the first occurrence's order`() {
        val layout = HomeLayout(
            listOf(HomePage("p1", PageType.APPS, "A", PageConfig(itemKeys = listOf("b", "", "a", "b", " ")))),
        )
        assertEquals(listOf("b", "a"), HomeLayout.sanitize(layout).pages.single().config.itemKeys)
    }

    @Test
    fun `columns is pinned to one`() {
        val layout = HomeLayout(listOf(HomePage("p1", PageType.APPS, "A", PageConfig(columns = 4))))
        assertEquals(1, HomeLayout.sanitize(layout).pages.single().config.columns)
    }

    @Test
    fun `sanitize is idempotent`() {
        val messy = HomeLayout(
            listOf(
                HomePage("", PageType.TODAY, ""),
                HomePage("dup", PageType.APPS, "A", PageConfig(itemKeys = listOf("k", "k"))),
                HomePage("dup", PageType.APPS, "B"),
            ),
            activePageId = "nope",
        )
        val once = HomeLayout.sanitize(messy)
        assertEquals(once, HomeLayout.sanitize(once))
    }

    @Test
    fun `the active index resolves and the page lookup finds by id`() {
        val layout = HomeLayout.DEFAULT.setActivePage("p_plan")
        assertEquals(1, layout.activeIndex)
        assertEquals("p_plan", layout.page("p_plan")?.id)
        assertNull(layout.page("absent"))
    }

    @Test
    fun `add page inserts after the active one and refuses past the limit`() {
        val added = HomeLayout.DEFAULT.setActivePage("p_today").addPage(PageType.APPS, "Extra")!!
        assertEquals(listOf("p_today", added.second, "p_plan", "p_launch"), added.first.pages.map { it.id })

        val full = HomeLayout((1..HomeLayout.MAX_PAGES).map { HomePage("p$it", PageType.APPS, "Page $it") })
        assertNull(full.addPage(PageType.APPS, "One too many"))
    }

    @Test
    fun `renaming to blank is ignored rather than stored`() {
        assertEquals("Today", HomeLayout.DEFAULT.renamePage("p_today", "   ").page("p_today")?.name)
        assertEquals("Planner", HomeLayout.DEFAULT.renamePage("p_today", " Planner ").page("p_today")?.name)
    }

    @Test
    fun `removing the last page is refused`() {
        val single = HomeLayout(listOf(HomePage("p1", PageType.APPS, "A")), activePageId = "p1")
        assertEquals(single, single.removePage("p1"))
    }

    @Test
    fun `removing the active page moves the active id to a neighbour`() {
        val removed = HomeLayout.DEFAULT.setActivePage("p_launch").removePage("p_launch")
        assertEquals(2, removed.pages.size)
        assertTrue(removed.activePageId != "p_launch")
        assertTrue(removed.pages.any { it.id == removed.activePageId })
    }

    @Test
    fun `removing a page that is not active leaves the active id alone`() {
        assertEquals("p_today", HomeLayout.DEFAULT.removePage("p_plan").activePageId)
    }

    @Test
    fun `a page moved to a clamped index keeps its own id`() {
        val moved = HomeLayout.DEFAULT.movePage("p_today", 99)
        assertEquals(listOf("p_plan", "p_launch", "p_today"), moved.pages.map { it.id })
        assertEquals("p_today", moved.pages.last().id)
    }

    @Test
    fun `reordering never renumbers ids so the active page is unaffected`() {
        val before = HomeLayout.DEFAULT.setActivePage("p_plan")
        val after = before.movePage("p_today", 2)
        assertEquals("p_plan", after.activePageId)
        assertEquals("p_plan", after.pages[after.activeIndex].id)
    }

    @Test
    fun `a pin lands on the active apps page, else the first apps page, else nowhere`() {
        assertEquals("p_launch", HomeLayout.DEFAULT.setActivePage("p_launch").pinTargetPageId())
        // Active page is Today, which shows no apps, so the first apps page takes the pin.
        assertEquals("p_launch", HomeLayout.DEFAULT.setActivePage("p_today").pinTargetPageId())
        val noApps = HomeLayout(listOf(HomePage("p1", PageType.TODAY, "Today")), activePageId = "p1")
        assertNull(noApps.pinTargetPageId())
    }

    @Test
    fun `with apps page creates one only when there is none`() {
        val created = HomeLayout(listOf(HomePage("p1", PageType.TODAY, "Today")))
        assertEquals(2, created.withAppsPage().pages.size)
        assertEquals(HomeLayout.DEFAULT, HomeLayout.DEFAULT.withAppsPage())
    }

    @Test
    fun `removing an item everywhere leaves no page holding a hidden key`() {
        val layout = HomeLayout.DEFAULT
            .addItem("p_launch", "com.example.notes/.Main")
            .addItem("p_plan", "com.example.notes/.Main")
        assertEquals(0, layout.removeItemEverywhere("com.example.notes/.Main").pages.count {
            "com.example.notes/.Main" in it.config.itemKeys
        })
    }

    @Test
    fun `adding an item that is already there changes nothing`() {
        val once = HomeLayout.DEFAULT.addItem("p_launch", "k")
        assertEquals(once, once.addItem("p_launch", "k"))
        assertEquals(listOf("k"), once.page("p_launch")!!.config.itemKeys)
    }

    @Test
    fun `a blank item key is refused rather than stored`() {
        assertEquals(HomeLayout.DEFAULT, HomeLayout.DEFAULT.addItem("p_launch", "  "))
    }

    @Test
    fun `moving an item is a no-op for an index outside the list, and never crosses pages`() {
        val layout = HomeLayout.DEFAULT.addItems("p_launch", listOf("a", "b", "c"))
        assertEquals(listOf("b", "a", "c"), layout.moveItem("p_launch", 0, 1).page("p_launch")!!.config.itemKeys)
        // Past either end there is no such slot, so the order is left alone rather than the
        // item being dropped or wrapped around to the other end.
        assertEquals(listOf("a", "b", "c"), layout.moveItem("p_launch", 0, -5).page("p_launch")!!.config.itemKeys)
        assertEquals(listOf("a", "b", "c"), layout.moveItem("p_launch", 0, 99).page("p_launch")!!.config.itemKeys)
        // A page with no items has nowhere to move one to, and no other page is touched.
        assertEquals(0, layout.moveItem("p_plan", 0, 1).page("p_plan")!!.config.itemKeys.size)
        assertEquals(listOf("a", "b", "c"), layout.moveItem("p_launch", 7, 0).page("p_launch")!!.config.itemKeys)
    }

    @Test
    fun `re-typing a page keeps the apps it was holding`() {
        val layout = HomeLayout.DEFAULT.addItem("p_launch", "k").setType("p_launch", PageType.NOTES)
        assertEquals(PageType.NOTES, layout.page("p_launch")!!.type)
        assertEquals(listOf("k"), layout.page("p_launch")!!.config.itemKeys)
    }

    @Test
    fun `the default layout names the three pages migration builds on`() {
        assertEquals(listOf("p_today", "p_plan", "p_launch"), HomeLayout.DEFAULT.pages.map { it.id })
        assertEquals(PageType.TODAY, HomeLayout.DEFAULT.page("p_today")?.type)
        assertEquals(PageType.PLAN, HomeLayout.DEFAULT.page("p_plan")?.type)
        assertEquals(PageType.APPS, HomeLayout.DEFAULT.page("p_launch")?.type)
    }

    @Test
    fun `every default page type has a name to fall back to`() {
        PageType.entries.forEach { assertNotEquals("", it.defaultName()) }
    }

    @Test
    fun `a generated id is unique per call and safe inside the encoding`() {
        val ids = (0 until 200).map { HomeLayout.newId() }
        assertEquals(ids.size, ids.distinct().size)
        assertTrue(ids.all { it.startsWith("p_") && it.none { c -> c == '"' || c == ',' || c == '\\' } })
    }
}
