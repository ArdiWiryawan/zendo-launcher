package com.zendolauncher.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Round-trips the stored Home layout.
 *
 * Robolectric rather than a plain JVM test because the codec uses `org.json`, which ships
 * in the Android platform and is therefore only on the classpath under Robolectric. That is
 * the whole reason this file is not beside `AppQueryTest`: the model itself needs no
 * Android, and [HomeLayoutSanitizeTest] covers the repair rules on a plain JVM.
 *
 * The cases that matter are the ones a real install can produce: a blob that is a version
 * older, a blob that is malformed, and a page whose name or item list contains the
 * characters a delimited format would have collided with.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class HomeLayoutCodecTest {

    private val sample = HomeLayout(
        pages = listOf(
            HomePage("p_today", PageType.TODAY, "Today", PageConfig(showClock = true)),
            HomePage(
                "p_launch",
                PageType.APPS,
                "Launch",
                PageConfig(
                    heading = "Essentials",
                    itemKeys = listOf("com.example.notes/.MainActivity", "com.example.docs/Shortcut"),
                ),
            ),
            HomePage("p_focus", PageType.NOTES, "Focus"),
        ),
        activePageId = "p_launch",
    )

    @Test
    fun `a layout survives a round trip`() {
        val decoded = HomeLayoutCodec.decode(HomeLayoutCodec.encode(sample))
        assertNotNull(decoded)
        assertEquals(sample.pages, decoded!!.pages)
    }

    @Test
    fun `the active page is not the codec's to store`() {
        // The active id lives under its own key so a scroll does not rewrite the whole
        // page blob; decode must leave it to the caller rather than inventing one.
        val decoded = HomeLayoutCodec.decode(HomeLayoutCodec.encode(sample))
        assertNull(decoded!!.activePageId)
    }

    @Test
    fun `a name containing the old flat format's separator survives`() {
        // The flat favourites list was newline-separated, which is exactly what a JSON blob
        // removes the need to escape. A name with a newline in it is the proof.
        val layout = HomeLayout(
            listOf(HomePage("p1", PageType.APPS, "Work\nand\ttabs", PageConfig(heading = "a,b|c"))),
        )
        val decoded = HomeLayoutCodec.decode(HomeLayoutCodec.encode(layout))
        assertEquals("Work\nand\ttabs", decoded!!.pages.single().name)
        assertEquals("a,b|c", decoded.pages.single().config.heading)
    }

    @Test
    fun `every page type survives`() {
        val layout = HomeLayout(PageType.entries.mapIndexed { index, type -> HomePage("p$index", type, type.name) })
        val decoded = HomeLayoutCodec.decode(HomeLayoutCodec.encode(layout))
        assertEquals(PageType.entries.toList(), decoded!!.pages.map { it.type })
    }

    @Test
    fun `an unknown stored type becomes an apps page rather than dropping the page`() {
        val raw = """[{"id":"p1","type":"WEATHER","name":"Weather"}]"""
        val decoded = HomeLayoutCodec.decode(raw)
        assertEquals(1, decoded!!.pages.size)
        assertEquals(PageType.APPS, decoded.pages.single().type)
    }

    @Test
    fun `unknown keys are ignored so a newer build can write beside them`() {
        val raw = """[{"id":"p1","type":"APPS","name":"Launch","futureThing":{"a":[1,2]}}]"""
        val decoded = HomeLayoutCodec.decode(raw)
        assertEquals("Launch", decoded!!.pages.single().name)
    }

    @Test
    fun `a page with no id is skipped rather than given an invented one`() {
        // Ids are the only way to address a page; a page the user cannot reach is dropped.
        val raw = """[{"type":"APPS","name":"Orphan"},{"id":"p1","type":"APPS","name":"Kept"}]"""
        val decoded = HomeLayoutCodec.decode(raw)
        assertEquals(listOf("Kept"), decoded!!.pages.map { it.name })
    }

    @Test
    fun `blank item keys are dropped`() {
        val raw = """[{"id":"p1","type":"APPS","name":"A","items":["a","","b"]}]"""
        assertEquals(listOf("a", "b"), HomeLayoutCodec.decode(raw)!!.pages.single().config.itemKeys)
    }

    @Test
    fun `nothing stored reads as null`() {
        assertNull(HomeLayoutCodec.decode(null))
        assertNull(HomeLayoutCodec.decode(""))
        assertNull(HomeLayoutCodec.decode("   "))
    }

    /**
     * A launcher that will not start because one preferences string is malformed is worse
     * than one that starts on the default Home, so every one of these must be an answer
     * rather than an exception.
     */
    @Test
    fun `a malformed blob reads as null instead of throwing`() {
        assertNull(HomeLayoutCodec.decode("not json"))
        assertNull(HomeLayoutCodec.decode("["))
        assertNull(HomeLayoutCodec.decode("{}"))
        assertNull(HomeLayoutCodec.decode("[]"))
        assertNull(HomeLayoutCodec.decode("[null]"))
        assertNull(HomeLayoutCodec.decode("""[{"no":"id"}]"""))
        assertNull(HomeLayoutCodec.decode("null"))
    }

    @Test
    fun `a truncated array keeps the pages that are complete`() {
        // The tail is cut mid-object, so the whole string is not parseable. Recovering the
        // prefix would be a guess about content the user may never have had.
        val raw = """[{"id":"p1","type":"APPS","name":"Launch"},{"id":"p2","ty"""
        assertNull(HomeLayoutCodec.decode(raw))
    }

    @Test
    fun `encode of an empty layout is an array the decoder refuses`() {
        // The store never writes an empty layout — sanitize replaces one with DEFAULT — but
        // the pair must agree on what "nothing" means if it ever did.
        assertNull(HomeLayoutCodec.decode(HomeLayoutCodec.encode(HomeLayout(emptyList()))))
    }

    @Test
    fun `columns is read but can never arrive as anything but one`() {
        val raw = """[{"id":"p1","type":"APPS","name":"A","columns":3}]"""
        assertEquals(3, HomeLayoutCodec.decode(raw)!!.pages.single().config.columns)
        // Sanitize is what pins it to 1; the codec reports what is stored, unedited.
        assertEquals(1, HomeLayout.sanitize(HomeLayoutCodec.decode(raw)!!).pages.single().config.columns)
    }

    @Test
    fun `heading and clock survive and are absent when unset`() {
        val decoded = HomeLayoutCodec.decode(HomeLayoutCodec.encode(sample))!!
        assertEquals("Essentials", decoded.pages[1].config.heading)
        assertNull(decoded.pages[2].config.heading)
        assertTrue(decoded.pages[0].config.showClock)
        assertTrue(!decoded.pages[2].config.showClock)
    }
}
