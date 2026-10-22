package com.zendolauncher.ui

import com.zendolauncher.data.BackupPrefs
import com.zendolauncher.data.LauncherSettings
import com.zendolauncher.data.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The backup's settings mapped onto the live ones. The backup format is older than this
 * build's settings model, so the mapping is deliberately partial: it restores the
 * behaviour settings that still exist and leaves everything else — Home pages, hidden
 * apps, the calendar selection — exactly as the user had it.
 */
class BackupRestoreMappingTest {

    @Test
    fun `a restore brings back the behaviour settings`() {
        val restored = LauncherSettings().restoredFromBackup(
            BackupPrefs(
                theme = ThemeMode.LIGHT,
                reduceMotion = true,
                pauseEnabled = true,
                pauseMessage = "Think first.",
                windowEnabled = true,
                windowStartMinute = 21 * 60,
                windowEndMinute = 6 * 60,
            ),
        )
        assertEquals(ThemeMode.LIGHT, restored.theme)
        assertTrue(restored.reduceMotion)
        assertTrue(restored.pauseEnabled)
        assertEquals("Think first.", restored.pauseMessage)
        assertTrue(restored.windowEnabled)
        assertEquals(21 * 60, restored.windowStartMinute)
        assertEquals(6 * 60, restored.windowEndMinute)
    }

    /**
     * A restore must not touch what a backup never carried. Home pages are the clearest
     * case: they hold the user's pinned apps, and a restore that reset them would silently
     * empty the launcher's only page.
     */
    @Test
    fun `a restore leaves the Home layout and hidden apps alone`() {
        val live = LauncherSettings(hidden = setOf("com.example.games/.Main"))
        val restored = live.restoredFromBackup(BackupPrefs(theme = ThemeMode.DARK))
        assertEquals(live.home, restored.home)
        assertEquals(live.hidden, restored.hidden)
        assertEquals(ThemeMode.DARK, restored.theme)
    }

    @Test
    fun `the default theme is dark for both an empty settings object and an old file`() {
        assertEquals(ThemeMode.DARK, LauncherSettings().theme)
        assertEquals(ThemeMode.DARK, BackupPrefs().theme)
        assertFalse(LauncherSettings().reduceMotion)
    }
}
