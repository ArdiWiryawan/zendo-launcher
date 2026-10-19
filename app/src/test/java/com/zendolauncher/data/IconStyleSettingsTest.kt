package com.zendolauncher.data

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The icon-appearance settings: the three icon styles, the wash tint, the wallpaper
 * match, and the Home backdrop. Run against the real `SharedPreferences` file, like
 * [HomeMigrationTest], because the behaviour under test is what the store does with
 * what is actually on disk.
 *
 * The behaviour being pinned:
 *
 *  - a fresh install is monochrome on cream with no backdrop;
 *  - a full round-trip through the store preserves every value;
 *  - the pre-icon_style boolean is still read: true meant monochrome, false meant the
 *    app's own icon, and an install from before the change keeps its appearance;
 *  - a stored `icon_style` wins over the legacy boolean, so a downgrade then an
 *    upgrade cannot flip the style back;
 *  - a tint that is not a plain hex colour is replaced by the cream default on read
 *    and on write, so a consumer never has to parse defensively;
 *  - the legacy boolean is still written, so a downgrade finds its toggle.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class IconStyleSettingsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Before
    fun clearPrefs() {
        prefs.edit { clear() }
    }

    private fun store() = SettingsStore(context)

    @Test
    fun `a fresh install is monochrome on cream with no backdrop`() {
        val settings = store().read()
        assertEquals(IconStyle.MONOCHROME, settings.iconStyle)
        assertEquals("#EDE6D8", settings.iconTint)
        assertFalse(settings.iconTintMatchWallpaper)
        assertFalse(settings.homeBackdrop)
    }

    @Test
    fun `every icon-appearance value round-trips through the store`() {
        store().update {
            it.copy(
                iconStyle = IconStyle.TINTED,
                iconTint = "#C9A96A",
                iconTintMatchWallpaper = true,
                homeBackdrop = true,
            )
        }
        val settings = store().read()
        assertEquals(IconStyle.TINTED, settings.iconStyle)
        assertEquals("#C9A96A", settings.iconTint)
        assertTrue(settings.iconTintMatchWallpaper)
        assertTrue(settings.homeBackdrop)
    }

    /**
     * The migration the kdoc promises: with no `icon_style` key at all, the old boolean
     * decides. true was "desaturated", which is MONOCHROME; false was "the app's own
     * icon", which is NATIVE.
     */
    @Test
    fun `legacy monochrome true upgrades to monochrome`() {
        prefs.edit { putBoolean(KEY_MONOCHROME_ICONS, true) }
        assertEquals(IconStyle.MONOCHROME, store().read().iconStyle)
    }

    @Test
    fun `legacy monochrome false upgrades to native`() {
        prefs.edit { putBoolean(KEY_MONOCHROME_ICONS, false) }
        assertEquals(IconStyle.NATIVE, store().read().iconStyle)
    }

    /** A stored style is the user's later choice and must beat the frozen legacy key. */
    @Test
    fun `a stored icon style wins over the legacy boolean`() {
        prefs.edit {
            putBoolean(KEY_MONOCHROME_ICONS, false)
            putString(KEY_ICON_STYLE, IconStyle.MONOCHROME.name)
        }
        assertEquals(IconStyle.MONOCHROME, store().read().iconStyle)
    }

    /** An unknown style name is corruption, and the legacy key is the honest fallback. */
    @Test
    fun `an unreadable style name falls back to the legacy boolean`() {
        prefs.edit {
            putBoolean(KEY_MONOCHROME_ICONS, false)
            putString(KEY_ICON_STYLE, "SEPIA")
        }
        assertEquals(IconStyle.NATIVE, store().read().iconStyle)
    }

    @Test
    fun `an unreadable stored tint becomes the cream default on read`() {
        prefs.edit { putString(KEY_ICON_TINT, "not-a-colour") }
        assertEquals("#EDE6D8", store().read().iconTint)
    }

    @Test
    fun `a short hex is not a colour and becomes cream`() {
        prefs.edit { putString(KEY_ICON_TINT, "#12") }
        assertEquals("#EDE6D8", store().read().iconTint)
    }

    /** The write path sanitizes too, so the stored value can never be unparseable. */
    @Test
    fun `an invalid tint is sanitized before it is written`() {
        store().update { it.copy(iconTint = "#GGGGGG") }
        assertEquals("#EDE6D8", prefs.getString(KEY_ICON_TINT, null))
    }

    /** Case is normalised and the leading hash is always present in what is stored. */
    @Test
    fun `a tint is stored canonically`() {
        store().update { it.copy(iconTint = "c9a96a") }
        assertEquals("#C9A96A", prefs.getString(KEY_ICON_TINT, null))
        assertEquals("#C9A96A", store().read().iconTint)
    }

    /**
     * The downgrade contract: every write keeps the old boolean in step with the style,
     * because a build that predates `icon_style` reads only that key.
     */
    @Test
    fun `a write keeps the legacy boolean in step`() {
        store().update { it.copy(iconStyle = IconStyle.NATIVE, monochromeIcons = false) }
        assertFalse(prefs.getBoolean(KEY_MONOCHROME_ICONS, true))

        store().update { it.copy(iconStyle = IconStyle.MONOCHROME, monochromeIcons = true) }
        assertTrue(prefs.getBoolean(KEY_MONOCHROME_ICONS, false))
    }

    /** Reading is not writing: nothing appears on disk until the user changes something. */
    @Test
    fun `reading alone writes no icon keys`() {
        store().read()
        assertTrue(KEY_ICON_STYLE !in prefs)
        assertTrue(KEY_ICON_TINT !in prefs)
        assertTrue(KEY_HOME_BACKDROP !in prefs)
    }

    private companion object {
        const val PREFS = "launcher_settings"
        const val KEY_MONOCHROME_ICONS = "monochrome_icons"
        const val KEY_ICON_STYLE = "icon_style"
        const val KEY_ICON_TINT = "icon_tint"
        const val KEY_HOME_BACKDROP = "home_backdrop"
    }
}
