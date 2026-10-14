package com.zendolauncher.data

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zendolauncher.core.HomeLayout
import com.zendolauncher.core.HomeLayoutCodec
import com.zendolauncher.core.PageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The upgrade from 0.2.3's single Home to pages, run against the real
 * `SharedPreferences` file rather than a mock — the point of the test is what the store
 * does with what is actually on disk.
 *
 * The behaviour being pinned:
 *
 *  - the legacy `favourites` list survives as the Launch page's items;
 *  - the legacy key is *read and not written*, so a downgrade to 0.2.3 still finds it;
 *  - a hidden app stays hidden;
 *  - a PWA shortcut key travels through untouched — it is an opaque string here and
 *    resolving it stays the shortcut repository's job;
 *  - migration runs once. After any write the blob exists and the legacy keys are never
 *    consulted again, which is what makes a later edit by the user win over the old list.
 *
 * Robolectric because `SharedPreferences` is a platform class; the file it uses is the
 * app's real preferences file, so the tests here reset the keys they depend on instead of
 * assuming an empty install.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class HomeMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Before
    fun clearPrefs() {
        prefs.edit { clear() }
    }

    private fun store() = SettingsStore(context)

    /** Writes the legacy keys exactly as 0.2.3 would have, with no pages blob at all. */
    private fun writeLegacy(favourites: List<String>, hidden: List<String> = emptyList()) {
        prefs.edit {
            putString(KEY_FAVOURITES, favourites.joinToString("\n"))
            putString(KEY_HIDDEN, hidden.joinToString("\n"))
            remove(KEY_HOME_PAGES)
            remove(KEY_HOME_ACTIVE_PAGE)
            remove(KEY_HOME_PAGES_VERSION)
        }
    }

    @Test
    fun `a fresh install gets the default three pages`() {
        val home = store().read().home
        assertEquals(HomeLayout.DEFAULT, home)
        assertEquals(3, home.pages.size)
    }

    @Test
    fun `legacy favourites land on the launch page`() {
        writeLegacy(listOf("com.example.notes/.MainActivity", "com.example.docs/Shortcut"))
        val home = store().read().home
        val launch = home.page("p_launch")!!
        assertEquals(PageType.APPS, launch.type)
        assertEquals(
            listOf("com.example.notes/.MainActivity", "com.example.docs/Shortcut"),
            launch.config.itemKeys,
        )
        // The other two pages are the default, untouched.
        assertEquals(PageType.TODAY, home.page("p_today")?.type)
        assertEquals(PageType.NOTES, home.page("p_focus")?.type)
    }

    /**
     * The property the whole read-only migration rests on: nothing on the read path may
     * write, or a downgrade would find an empty list and a user's ordered Home would be
     * gone with no way back.
     */
    @Test
    fun `reading does not write, so a downgrade still finds the legacy keys`() {
        writeLegacy(listOf("com.example.notes/.MainActivity"), listOf("com.example.docs/.Main"))
        store().read()

        assertEquals("com.example.notes/.MainActivity", prefs.getString(KEY_FAVOURITES, null))
        assertEquals("com.example.docs/.Main", prefs.getString(KEY_HIDDEN, null))
        assertTrue(KEY_HOME_PAGES !in prefs)
    }

    @Test
    fun `migration is idempotent across repeated reads`() {
        writeLegacy(listOf("a", "b"))
        val first = store().read().home
        assertEquals(first, store().read().home)
        assertEquals(first, store().read().home)
    }

    @Test
    fun `hidden apps stay hidden`() {
        writeLegacy(favourites = emptyList(), hidden = listOf("com.example.games/.Main"))
        assertEquals(setOf("com.example.games/.Main"), store().read().hidden)
    }

    @Test
    fun `an empty legacy list is not distinguishable from a fresh install`() {
        writeLegacy(emptyList())
        assertEquals(HomeLayout.DEFAULT, store().read().home)
    }

    /**
     * The blob is the source of truth once it exists. A user who unpins a migrated app and
     * upgrades again must not have it come back from the legacy list.
     */
    @Test
    fun `a stored layout wins over the legacy list`() {
        writeLegacy(listOf("old.key"))
        store().read()
        // Simulate the write that any settings change performs.
        store().update { it.copy(clock24h = false) }
        assertNotNull(prefs.getString(KEY_HOME_PAGES, null))

        val home = store().read().home
        assertEquals(listOf("old.key"), home.page("p_launch")!!.config.itemKeys)
        // A later edit by the user now sticks, because the legacy list is not consulted.
        store().toggleFavourite("old.key")
        assertEquals(emptyList<String>(), store().read().home.page("p_launch")!!.config.itemKeys)
    }

    @Test
    fun `a write persists the pages blob, the active id and the version`() {
        store().update { it.copy(clock24h = true) }
        assertEquals(SettingsStore.HOME_PAGES_VERSION, prefs.getInt(KEY_HOME_PAGES_VERSION, -1))
        assertEquals("p_today", prefs.getString(KEY_HOME_ACTIVE_PAGE, null))
        val blob = prefs.getString(KEY_HOME_PAGES, null)
        assertNotNull(blob)
        assertEquals(HomeLayout.DEFAULT.pages, HomeLayoutCodec.decode(blob)!!.pages)
    }

    @Test
    fun `a corrupt blob falls back to the default instead of crashing the launcher`() {
        writeLegacy(emptyList())
        prefs.edit { putString(KEY_HOME_PAGES, "{ this is not a layout") }
        assertEquals(HomeLayout.DEFAULT, store().read().home)
    }

    @Test
    fun `an out-of-range stored layout is repaired on read`() {
        prefs.edit {
            putString(
                KEY_HOME_PAGES,
                """[{"id":"a","type":"APPS","name":"A"},{"id":"a","type":"APPS","name":"B"}]""",
            )
            remove(KEY_HOME_ACTIVE_PAGE)
        }
        val home = store().read().home
        assertEquals(2, home.pages.map { it.id }.distinct().size)
        assertEquals(home.pages.first().id, home.activePageId)
    }

    // ---- Pinning through the pages API ----------------------------------------------

    @Test
    fun `pinning from the drawer lands on the active apps page`() {
        val store = store()
        store.setActivePage("p_launch")
        store.toggleFavourite("com.example.notes/.Main")
        assertEquals(listOf("com.example.notes/.Main"), store.read().home.page("p_launch")?.config?.itemKeys)
        assertEquals("p_launch", store.read().home.activePageId)
    }

    /**
     * A pin made while the Today page is showing still has to be visible: it lands on the
     * first apps page and the active page follows it, rather than appearing to do nothing.
     */
    @Test
    fun `pinning while a page that shows no apps is active moves to the apps page`() {
        val store = store()
        store.setActivePage("p_today")
        store.toggleFavourite("com.example.notes/.Main")
        val home = store.read().home
        assertEquals(listOf("com.example.notes/.Main"), home.page("p_launch")?.config?.itemKeys)
        assertEquals("p_launch", home.activePageId)
    }

    @Test
    fun `pinning twice unpins, on whichever page holds the key`() {
        val store = store()
        store.setActivePage("p_launch")
        store.toggleFavourite("k")
        store.toggleFavourite("k")
        assertEquals(emptyList<String>(), store.read().home.page("p_launch")?.config?.itemKeys)
    }

    @Test
    fun `hiding a pinned app drops it from every page`() {
        val store = store()
        store.setActivePage("p_launch")
        store.toggleFavourite("k")
        store.toggleHidden("k")
        val home = store.read().home
        assertTrue(home.pages.none { "k" in it.config.itemKeys })
        assertTrue("k" in store.read().hidden)
    }

    @Test
    fun `a pin with no apps page anywhere creates one rather than being swallowed`() {
        val store = store()
        store.removePage("p_launch")
        store.toggleFavourite("k")
        val home = store.read().home
        assertTrue(home.pages.any { it.type == PageType.APPS })
        assertTrue(home.pages.any { "k" in it.config.itemKeys })
    }

    @Test
    fun `adding a page beyond the limit returns null and changes nothing`() {
        val store = store()
        while (store.read().home.pages.size < HomeLayout.MAX_PAGES) store.addPage()
        assertEquals(HomeLayout.MAX_PAGES, store.read().home.pages.size)
        assertEquals(null, store.addPage())
        assertEquals(HomeLayout.MAX_PAGES, store.read().home.pages.size)
    }

    @Test
    fun `the active page is restored from its own key`() {
        store().setActivePage("p_focus")
        assertEquals("p_focus", store().read().home.activePageId)
    }

    private companion object {
        const val PREFS = "launcher_settings"
        const val KEY_FAVOURITES = "favourites"
        const val KEY_HIDDEN = "hidden"
        const val KEY_HOME_PAGES = "home_pages"
        const val KEY_HOME_ACTIVE_PAGE = "home_active_page_id"
        const val KEY_HOME_PAGES_VERSION = "home_pages_version"
    }
}
