package com.zendolauncher.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zendolauncher.LauncherActivity
import com.zendolauncher.data.LayoutMode
import com.zendolauncher.data.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Executes the real Compose UI on the JVM through Robolectric. The activity under
 * test is the shipped [LauncherActivity] and the view model is the one the activity
 * created, so navigation and state flow are the production ones.
 *
 * SDK 36 is used because Robolectric 4.17 ships an android-all image for it
 * (16-robolectric-13921718), matching the app's compileSdk/targetSdk.
 *
 * Settings live in SharedPreferences, which Robolectric keeps for the whole JVM, so
 * every test resets the settings it depends on instead of assuming defaults.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class LauncherUiTest {

    @get:Rule
    val rule = createAndroidComposeRule<LauncherActivity>()

    private val viewModel: LauncherViewModel
        get() = ViewModelProvider(rule.activity)[LauncherViewModel::class.java]

    private fun textMatching(regex: Regex) = SemanticsMatcher("text matching ${regex.pattern}") { node ->
        node.config.getOrNull(SemanticsProperties.Text)?.any { regex.containsMatchIn(it.text) } == true
    }

    private fun resetSettings() {
        viewModel.setTheme(ThemeMode.SYSTEM)
        viewModel.setLayout(LayoutMode.TEXT)
        viewModel.setTodayEnabled(true)
        viewModel.setClock24h(true)
        viewModel.setZendoEnabled(false)
        viewModel.goHome()
        rule.waitForIdle()
    }

    @Before
    fun setUp() = resetSettings()

    /**
     * Toggles appear in document order: 24-hour clock, Show today, Connect Zendo.
     * The rows themselves carry no semantics, so they are addressed by position.
     * Settings scroll, so later rows must be scrolled into view before a tap can
     * land on them.
     */
    private fun toggleAt(index: Int) =
        rule.onAllNodes(isToggleable())[index].performScrollTo()

    /** The clock is "HH:mm" (24-hour) and the date is "EEEE, d MMMM". */
    private val clockPattern = Regex("""\b\d{1,2}:\d{2}\b""")
    private val datePattern = Regex("""\p{L}+,\s*\d{1,2}\s+\p{L}+""")

    @Test
    fun homeScreen_composes_andShowsClockAndDate() {
        rule.onNodeWithText("Essentials").assertIsDisplayed()
        rule.onAllNodes(textMatching(clockPattern)).assertCountEquals(1)
        rule.onAllNodes(textMatching(datePattern)).assertCountEquals(1)
    }

    @Test
    fun homeScreen_showsEssentialsEmptyState_andBottomActions() {
        rule.onNodeWithText(
            "Nothing pinned yet. Open All apps and long-press an app to add it here.",
        ).assertIsDisplayed()
        rule.onNodeWithText("All apps").assertIsDisplayed()
        rule.onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test
    fun allAppsAction_navigatesToAppList_andHomeReturns() {
        rule.onNodeWithText("All apps").performClick()
        rule.waitForIdle()
        assertEquals(Screen.APPS, viewModel.state.value.screen)

        // The search field is present with its placeholder.
        rule.onNodeWithText("Search").assertIsDisplayed()
        rule.onNodeWithText("No launchable apps found.").assertIsDisplayed()

        rule.onNodeWithText("Home").performClick()
        rule.waitForIdle()
        assertEquals(Screen.HOME, viewModel.state.value.screen)
        rule.onNodeWithText("Essentials").assertIsDisplayed()
    }

    @Test
    fun goHome_fromSettings_returnsHome() {
        rule.onNodeWithText("Settings").performClick()
        rule.waitForIdle()
        assertEquals(Screen.SETTINGS, viewModel.state.value.screen)
        rule.onNodeWithText("24-hour clock").assertIsDisplayed()

        rule.onNodeWithText("Home").performClick()
        rule.waitForIdle()
        assertEquals(Screen.HOME, viewModel.state.value.screen)
    }

    @Test
    fun search_withNoMatches_showsNothingMatchesEmptyState() {
        viewModel.openApps()
        rule.waitForIdle()
        viewModel.setQuery("zzz")
        rule.waitForIdle()

        rule.onNodeWithText("Nothing matches \"zzz\".").assertIsDisplayed()
        rule.onNodeWithText("Results").assertIsDisplayed()
    }

    @Test
    fun search_isClearedWhenGoingHome() {
        viewModel.openApps()
        viewModel.setQuery("zzz")
        rule.waitForIdle()
        viewModel.goHome()
        rule.waitForIdle()
        assertEquals("", viewModel.state.value.query)
    }

    @Test
    fun todayBlock_absentWithDefaultUnavailableSource_andScreenStillRenders() {
        // Defaults: todayEnabled = true, zendoEnabled = false -> nothing to show.
        assertTrue(viewModel.state.value.settings.todayEnabled)
        assertFalse(viewModel.state.value.settings.zendoEnabled)
        rule.onAllNodesWithText("Today").assertCountEquals(0)
        rule.onNodeWithText("Essentials").assertIsDisplayed()

        // Turning the Zendo source on cannot produce content: the only shipped
        // source is UnavailableZendoSource, which always returns null.
        viewModel.setZendoEnabled(true)
        rule.waitForIdle()
        rule.onAllNodesWithText("Today").assertCountEquals(0)
        rule.onNodeWithText("Essentials").assertIsDisplayed()

        // Turning the section off keeps the screen rendering too.
        viewModel.setTodayEnabled(false)
        rule.waitForIdle()
        rule.onAllNodesWithText("Today").assertCountEquals(0)
        rule.onNodeWithText("Essentials").assertIsDisplayed()
    }

    @Test
    fun settings_24HourToggle_updatesState_andClockRenders12Hour() {
        viewModel.openSettings()
        rule.waitForIdle()
        assertTrue(viewModel.state.value.settings.clock24h)

        toggleAt(0).performClick()
        rule.waitForIdle()
        assertFalse(viewModel.state.value.settings.clock24h)

        viewModel.goHome()
        rule.waitForIdle()
        // Still exactly one clock, now in 12-hour form with an am/pm marker.
        rule.onAllNodes(textMatching(clockPattern)).assertCountEquals(1)
        rule.onAllNodes(textMatching(Regex("""\b(AM|PM)\b"""))).assertCountEquals(1)
    }

    @Test
    fun settings_themeChoice_updatesState() {
        viewModel.openSettings()
        rule.waitForIdle()
        assertEquals(ThemeMode.SYSTEM, viewModel.state.value.settings.theme)

        rule.onNodeWithText("Dark").performClick()
        rule.waitForIdle()
        assertEquals(ThemeMode.DARK, viewModel.state.value.settings.theme)

        rule.onNodeWithText("Light").performClick()
        rule.waitForIdle()
        assertEquals(ThemeMode.LIGHT, viewModel.state.value.settings.theme)
    }

    @Test
    fun settings_layoutChoice_updatesState() {
        viewModel.openSettings()
        rule.waitForIdle()
        assertEquals(LayoutMode.TEXT, viewModel.state.value.settings.layout)

        rule.onNodeWithText("Icon + text").performClick()
        rule.waitForIdle()
        assertEquals(LayoutMode.ICON_TEXT, viewModel.state.value.settings.layout)
    }

    @Test
    fun settings_showTodayToggle_updatesState() {
        viewModel.openSettings()
        rule.waitForIdle()
        assertTrue(viewModel.state.value.settings.todayEnabled)

        toggleAt(1).performClick()
        rule.waitForIdle()
        assertFalse(viewModel.state.value.settings.todayEnabled)
    }

    @Test
    fun homeScreen_rendersRefreshOnlyInIconTextLayout() {
        rule.onAllNodesWithText("Refresh").assertCountEquals(0)
        viewModel.setLayout(LayoutMode.ICON_TEXT)
        rule.waitForIdle()
        rule.onAllNodesWithText("Refresh").assertCountEquals(1)
    }

    @Test
    fun settings_showsEmptyPinnedAndHiddenSections() {
        viewModel.openSettings()
        rule.waitForIdle()
        rule.onNodeWithText("No pinned apps.").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("No hidden apps.").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("About").performScrollTo().assertIsDisplayed()
    }
}
