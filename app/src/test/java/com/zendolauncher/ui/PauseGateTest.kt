package com.zendolauncher.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zendolauncher.LauncherActivity
import com.zendolauncher.model.LaunchTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Pause gate: distract-listed + pause on parks in pendingPause; everything else launches. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class PauseGateTest {

    @Suppress("DEPRECATION")
    @get:Rule
    val rule = createAndroidComposeRule<LauncherActivity>()

    private val vm: LauncherViewModel
        get() = ViewModelProvider(rule.activity)[LauncherViewModel::class.java]

    // Null activity short-circuits AppLauncher to its Toast path, so direct launches
    // stay JVM-safe (Robolectric discovers no apps); the gate only reads target.key.
    private fun target(pkg: String = "com.example.notes") =
        LaunchTarget(packageName = pkg, activityName = null, label = "Notes")

    @Before
    fun setUp() {
        vm.setPauseEnabled(false)
        vm.state.value.settings.distract.toList().forEach { vm.toggleDistract(it) }
        vm.cancelPause()
        vm.goHome()
        rule.waitForIdle()
    }

    @Test
    fun gate_blocksDistractListed_untilConfirm() {
        val t = target()
        vm.setPauseEnabled(true)
        vm.toggleDistract(t.key)
        rule.waitForIdle()

        vm.launch(t)
        rule.waitForIdle()
        assertEquals(t, vm.state.value.pendingPause)

        vm.confirmPause()
        rule.waitForIdle()
        assertNull(vm.state.value.pendingPause)
    }

    @Test
    fun cancel_dropsPending() {
        val t = target()
        vm.setPauseEnabled(true)
        vm.toggleDistract(t.key)
        rule.waitForIdle()

        vm.launch(t)
        rule.waitForIdle()
        assertEquals(t, vm.state.value.pendingPause)

        vm.cancelPause()
        rule.waitForIdle()
        assertNull(vm.state.value.pendingPause)
    }

    @Test
    fun unrestricted_launchesDirectly() {
        vm.setPauseEnabled(true)
        vm.toggleDistract("com.other/.Main")
        rule.waitForIdle()

        vm.launch(target())
        rule.waitForIdle()
        assertNull(vm.state.value.pendingPause)
    }

    @Test
    fun pauseDisabled_launchesDirectly() {
        val t = target()
        vm.toggleDistract(t.key)
        rule.waitForIdle()

        vm.launch(t)
        rule.waitForIdle()
        assertNull(vm.state.value.pendingPause)
    }

    @Test
    fun toggleDistract_addsAndRemoves() {
        val key = target().key
        assertFalse(key in vm.state.value.settings.distract)
        vm.toggleDistract(key)
        rule.waitForIdle()
        assertTrue(key in vm.state.value.settings.distract)
        vm.toggleDistract(key)
        rule.waitForIdle()
        assertFalse(key in vm.state.value.settings.distract)
    }

    @Test
    fun emptyDistract_neverGates() {
        assertTrue(vm.state.value.settings.distract.isEmpty())
        vm.setPauseEnabled(true)
        rule.waitForIdle()

        vm.launch(target())
        rule.waitForIdle()
        assertNull(vm.state.value.pendingPause)
    }

    @Test
    fun settings_rendersHonestSystemSettingsShortcuts() {
        vm.openSettings()
        rule.waitForIdle()
        rule.onNodeWithText("Internet & Wi-Fi").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Display & lock screen").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Sound & notifications").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun pauseDialog_rendersPromptAndCancelDismisses() {
        val t = target()
        vm.setPauseEnabled(true)
        vm.toggleDistract(t.key)
        vm.launch(t)
        rule.waitForIdle()

        rule.onNodeWithText("Take a breath. Open it anyway?").assertIsDisplayed()
        rule.onNodeWithText("Cancel").performClick()
        rule.waitForIdle()
        assertNull(vm.state.value.pendingPause)
    }

    @Test
    fun backFromSettings_returnsToDrawerAndPreservesQuery() {
        vm.openApps()
        vm.setQuery("note")
        vm.openSettings(Screen.APPS)
        rule.waitForIdle()
        assertEquals(Screen.SETTINGS, vm.state.value.screen)

        vm.backFromSettings()
        rule.waitForIdle()
        assertEquals(Screen.APPS, vm.state.value.screen)
        assertEquals("note", vm.state.value.query)
    }

    @Test
    fun systemSettings_dispatchesDisplayIntent() {
        vm.openSettings()
        rule.waitForIdle()
        rule.onNodeWithText("Display & lock screen").performScrollTo().performClick()
        rule.waitForIdle()

        val started = shadowOf(rule.activity).nextStartedActivity
        assertNotNull(started)
        assertEquals(Settings.ACTION_DISPLAY_SETTINGS, started.action)
    }

    @Test
    fun wallpaperRow_resolvesSystemWallpaperPicker() {
        vm.openSettings()
        rule.waitForIdle()
        rule.onNodeWithText("Wallpaper (system picker)").performScrollTo().performClick()
        rule.waitForIdle()

        val started = shadowOf(rule.activity).nextStartedActivity
        assertNotNull(started)
        assertEquals(Intent.ACTION_SET_WALLPAPER, started.action)
    }
}

