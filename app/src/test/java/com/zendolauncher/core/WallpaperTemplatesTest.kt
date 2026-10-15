package com.zendolauncher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperTemplatesTest {

    @Test
    fun `three bundled designs with quiet stone as first and default`() {
        assertEquals(3, WallpaperTemplates.ALL.size)
        assertEquals(WallpaperTemplates.DEFAULT_ID, WallpaperTemplates.ALL.first().id)
        assertNotNull(WallpaperTemplates.byId(WallpaperTemplates.DEFAULT_ID))
        assertEquals(WallpaperTemplates.DEFAULT_ID, WallpaperTemplates.default().id)
    }

    @Test
    fun `ids are unique and resolvable`() {
        val ids = WallpaperTemplates.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { assertNotNull(WallpaperTemplates.byId(it)) }
    }

    @Test
    fun `typography only where the design calls for it, and the exact reminder line is kept`() {
        val quiet = WallpaperTemplates.byId("quiet_stone")!!
        assertNull(quiet.caption)
        assertNull(quiet.wordmark)
        // The artwork line is not translated, abbreviated or smart-quoted.
        assertEquals("Kembali ke yang penting.", WallpaperTemplates.byId("kembali")!!.caption)
        val less = WallpaperTemplates.byId("less_but_better")!!
        assertEquals("Less, but better", less.caption)
        assertEquals("ZENDO", less.wordmark)
    }

    @Test
    fun `every design stays grayscale`() {
        // No saturated color templates: every base color must be near-gray (r≈g≈b).
        WallpaperTemplates.ALL.forEach { t ->
            val c = t.baseColor.toInt()
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val spread = maxOf(r, g, b) - minOf(r, g, b)
            assertTrue("${t.id} spread $spread", spread <= 12)
        }
    }
}
