package com.zendolauncher.ui

import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.toArgb
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zendolauncher.core.WallpaperTemplates
import com.zendolauncher.data.IconStyle
import com.zendolauncher.resolvedIconTint
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The tint resolution path — previously untested, and the one place a stored string,
 * a settings enum, a wallpaper id and the duotone matrix meet.
 *
 * Precedence, as the resolver implements it:
 *
 *  - `NATIVE` returns the stored tint (which it never draws with);
 *  - `MONOCHROME` with the match off returns the stored swatch;
 *  - `MONOCHROME` with the match on, and `TINTED` always, follow the *applied* design's
 *    `dominantAccent()`;
 *  - no applied design — or an id the catalogue no longer knows — falls back to the
 *    default design's accent, and a stored tint that cannot be read falls back to cream.
 *
 * The duotone matrix is checked by hand-applying it the way the render pipeline does,
 * against the curve the code documents: `out.c = L * (tintC - 0.08) + 0.08` with the
 * value-only luminance `L`.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class IconTintResolutionTest {

    private val gold = "#C9A96A"
    private val cream = "#EDE6D8"

    private fun accentOf(id: String): String {
        val accent = requireNotNull(WallpaperTemplates.byId(id)).dominantAccent()
        return String.format(Locale.US, "#%06X", accent.toArgb() and 0xFFFFFF)
    }

    @Test
    fun `tinted follows the applied design's accent, match toggle or not`() {
        assertEquals(gold, resolvedIconTint(IconStyle.TINTED, cream, false, "less_but_better"))
        assertEquals(gold, resolvedIconTint(IconStyle.TINTED, cream, true, "less_but_better"))
    }

    @Test
    fun `with nothing applied the accent falls back to the default design`() {
        assertEquals(
            accentOf(WallpaperTemplates.DEFAULT_ID),
            resolvedIconTint(IconStyle.TINTED, cream, false, null),
        )
    }

    @Test
    fun `an id the catalogue does not know falls back the same way`() {
        assertEquals(
            accentOf(WallpaperTemplates.DEFAULT_ID),
            resolvedIconTint(IconStyle.TINTED, cream, false, "gone-from-the-catalogue"),
        )
    }

    @Test
    fun `monochrome follows the applied design only when asked`() {
        assertEquals(cream, resolvedIconTint(IconStyle.MONOCHROME, cream, false, "less_but_better"))
        assertEquals(gold, resolvedIconTint(IconStyle.MONOCHROME, cream, true, "less_but_better"))
    }

    @Test
    fun `native keeps the stored tint untouched`() {
        assertEquals(cream, resolvedIconTint(IconStyle.NATIVE, cream, true, "less_but_better"))
    }

    @Test
    fun `an unreadable stored tint resolves to cream`() {
        assertEquals(parseTint(cream), parseTint("not-a-colour"))
    }

    /**
     * Pure red under the gold tint, applied the way the pipeline applies a packed 4x5
     * matrix (rows `[r, g, b, a, offset]`, channels 0..255):
     *
     *   L = 0.213 * 255 = 54.315          (value-only desaturation: luminance in, hue out)
     *   out.r = (0xC9/255 - 0.08) * L + 0.08*255 = 0.7082 * 54.315 + 20.4 ≈ 58.9
     *   out.g = (0xA9/255 - 0.08) * L + 20.4 = 0.5827 * 54.315 + 20.4 ≈ 52.0
     *   out.b = (0x6A/255 - 0.08) * L + 20.4 = 0.3357 * 54.315 + 20.4 ≈ 38.6
     *
     * and pure black lands on the floor alone — every channel 20.4 — so shadows never
     * reach true black under a tint, which is what makes cream read as warm paper.
     */
    @Test
    fun `the duotone curve washes luminance toward the tint above its floor`() {
        val out = applyToPixel(duotoneMatrix(gold), r = 255f, g = 0f, b = 0f, a = 255f)
        val luminance = 0.213f * 255f
        val floor = 0.08f * 255f
        assertEquals((0xC9 / 255f - 0.08f) * luminance + floor, out[0], 0.5f)
        assertEquals((0xA9 / 255f - 0.08f) * luminance + floor, out[1], 0.5f)
        assertEquals((0x6A / 255f - 0.08f) * luminance + floor, out[2], 0.5f)

        val black = applyToPixel(duotoneMatrix(gold), r = 0f, g = 0f, b = 0f, a = 255f)
        assertEquals(floor, black[0], 0.1f)
        assertEquals(floor, black[1], 0.1f)
        assertEquals(floor, black[2], 0.1f)
    }

    /** A packed colour matrix against one pixel, per its documented application. */
    private fun applyToPixel(m: ColorMatrix, r: Float, g: Float, b: Float, a: Float): FloatArray {
        val v = m.values
        return floatArrayOf(
            v[0] * r + v[1] * g + v[2] * b + v[3] * a + v[4],
            v[5] * r + v[6] * g + v[7] * b + v[8] * a + v[9],
            v[10] * r + v[11] * g + v[12] * b + v[13] * a + v[14],
        )
    }
}
