package com.zendolauncher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperTemplatesTest {

    @Test
    fun `twelve bundled templates across solid and subtle`() {
        assertEquals(12, WallpaperTemplates.ALL.size)
        assertEquals(6, WallpaperTemplates.byCategory(WallpaperCategory.SOLID).size)
        assertEquals(6, WallpaperTemplates.byCategory(WallpaperCategory.SUBTLE).size)
        assertEquals(WallpaperTemplates.ALL.size, WallpaperTemplates.byCategory(null).size)
    }

    @Test
    fun `ids are unique and resolvable`() {
        val ids = WallpaperTemplates.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { assertTrue(WallpaperTemplates.byId(it) != null) }
    }

    @Test
    fun `first release is grayscale-first`() {
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
