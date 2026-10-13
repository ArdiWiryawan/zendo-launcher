package com.zendolauncher.core

import com.zendolauncher.model.LaunchTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppQueryTest {

    private fun app(pkg: String, label: String, activity: String = "Main") =
        LaunchTarget(packageName = pkg, activityName = "$pkg.$activity", label = label)

    private fun shortcut(pkg: String, id: String, label: String) =
        LaunchTarget(packageName = pkg, activityName = null, label = label, shortcutId = id)

    @Test
    fun `normalize strips case accents and padding`() {
        assertEquals("cafe", AppQuery.normalize("  Café "))
        assertEquals("uber", AppQuery.normalize("Über"))
    }

    @Test
    fun `alphabetical ignores case and accents`() {
        val sorted = AppQuery.alphabetical(
            listOf(app("a", "zebra"), app("b", "Apple"), app("c", "café"), app("d", "Banana")),
        )
        assertEquals(listOf("Apple", "Banana", "café", "zebra"), sorted.map { it.label })
    }

    @Test
    fun `hidden apps are removed`() {
        val apps = listOf(app("a", "Alpha"), app("b", "Beta"))
        val hidden = setOf(apps[0].key)
        assertEquals(listOf("Beta"), AppQuery.visible(apps, hidden).map { it.label })
    }

    @Test
    fun `favourites keep user order and drop stale keys`() {
        val apps = listOf(app("a", "Alpha"), app("b", "Beta"), app("c", "Gamma"))
        val order = listOf(apps[2].key, "gone.package/Main", apps[0].key)
        assertEquals(listOf("Gamma", "Alpha"), AppQuery.favourites(order, apps).map { it.label })
    }

    @Test
    fun `favourites can hold a shortcut key`() {
        val apps = listOf(app("a", "Alpha"), shortcut("com.browser", "pinned-1", "Docs"))
        val order = listOf(apps[1].key)
        assertEquals(listOf("Docs"), AppQuery.favourites(order, apps).map { it.label })
    }

    @Test
    fun `search ranks exact then prefix then word start then contains then initials`() {
        val apps = listOf(
            app("x", "My Notes"),
            app("y", "Notes"),
            app("z", "Notebook Archive"),
        )
        // "Notes" and "Notebook Archive" are both prefix matches (rank 1) and tie-break
        // alphabetically; "My Notes" only matches at a word start (rank 2).
        assertEquals(
            listOf("Notebook Archive", "Notes", "My Notes"),
            AppQuery.search(apps, "note").map { it.label },
        )
        // An exact match wins outright; "My Notes" still matches, but only at a word start.
        assertEquals(listOf("Notes", "My Notes"), AppQuery.search(apps, "notes").map { it.label })
        assertEquals(listOf("My Notes"), AppQuery.search(apps, "mn").map { it.label })
    }

    @Test
    fun `search is case and accent insensitive and returns alphabetical on empty query`() {
        val apps = listOf(app("a", "Café"), app("b", "Alpha"))
        assertEquals(listOf("Alpha", "Café"), AppQuery.search(apps, "").map { it.label })
        assertEquals(listOf("Café"), AppQuery.search(apps, "CAF").map { it.label })
    }

    @Test
    fun `search returns nothing for an unmatched query`() {
        assertEquals(emptyList<LaunchTarget>(), AppQuery.search(listOf(app("a", "Alpha")), "zzz"))
        assertNull(AppQuery.rank(app("a", "Alpha"), "zzz"))
    }

    @Test
    fun `key distinguishes activities in the same package`() {
        val first = app("com.pkg", "One", "First")
        val second = app("com.pkg", "Two", "Second")
        assertEquals(false, first.key == second.key)
        assertEquals("com.pkg/pinned", shortcut("com.pkg", "pinned", "Web").key)
    }
}
