package com.zendolauncher.ui

import com.zendolauncher.data.BackupPrefs
import com.zendolauncher.data.IconStyle
import com.zendolauncher.data.LauncherSettings
import com.zendolauncher.data.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The backup's monochrome boolean mapped onto the icon style. The restore used to copy
 * `monochromeIcons` but not `iconStyle`, while rendering and settings writes read
 * `iconStyle` — so a restored install kept whatever style it had before the restore, and
 * a downgrade-safety field disagreed with the field the UI draws from.
 *
 * The backup format predates the enum and carries only the boolean, so the boolean is
 * the single source of truth for the style: on restores [IconStyle.MONOCHROME], off
 * restores [IconStyle.NATIVE] — the same reading [com.zendolauncher.data.SettingsStore]
 * gives the legacy key — and the boolean is written back in step so a downgrade still
 * finds it.
 */
class BackupRestoreMappingTest {

    @Test
    fun `a backup with monochrome off restores the original icons`() {
        val restored = LauncherSettings().restoredFromBackup(BackupPrefs(monochromeIcons = false))
        assertEquals(IconStyle.NATIVE, restored.iconStyle)
        assertFalse(restored.monochromeIcons)
    }

    @Test
    fun `a backup with monochrome on restores monochrome`() {
        val restored = LauncherSettings().restoredFromBackup(BackupPrefs(monochromeIcons = true))
        assertEquals(IconStyle.MONOCHROME, restored.iconStyle)
        assertTrue(restored.monochromeIcons)
    }

    /**
     * The restore overwrites the live style even when the two disagree: the backup is the
     * thing being restored, and leaving a newer `iconStyle` in place while writing the old
     * boolean would recreate exactly the drift this mapping exists to close.
     */
    @Test
    fun `a restore replaces a disagreeing live style`() {
        val live = LauncherSettings(iconStyle = IconStyle.TINTED, monochromeIcons = false)
        val restored = live.restoredFromBackup(BackupPrefs(monochromeIcons = true))
        assertEquals(IconStyle.MONOCHROME, restored.iconStyle)
        assertTrue(restored.monochromeIcons)
    }

    /** The backup carries no tint and no backdrop, so a restore must not invent them. */
    @Test
    fun `a restore leaves what the backup does not carry alone`() {
        val live = LauncherSettings(iconTint = "#C9A96A", homeBackdrop = true)
        val restored = live.restoredFromBackup(BackupPrefs(theme = ThemeMode.DARK))
        assertEquals("#C9A96A", restored.iconTint)
        assertTrue(restored.homeBackdrop)
        assertEquals(ThemeMode.DARK, restored.theme)
    }
}
