package com.zendolauncher.ui

import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zendolauncher.BuildConfig
import com.zendolauncher.LauncherActivity
import com.zendolauncher.core.PageType
import com.zendolauncher.data.HomeAlignment
import com.zendolauncher.data.LabelCase
import com.zendolauncher.data.LauncherSettings
import com.zendolauncher.data.ThemeMode
import com.zendolauncher.model.LaunchTarget
import com.zendolauncher.ui.theme.BackdropStyle
import com.zendolauncher.ui.theme.LauncherTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
 * every test resets the settings and the Home layout it depends on instead of assuming
 * defaults. A fresh install is a single apps page; any other page is added by a test
 * that needs it, exactly as a user would.
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

    /**
     * Back to one apps page, dark, no pause. The layout is trimmed rather than rebuilt
     * because pages persist in SharedPreferences for the whole JVM: a Today or Plan page
     * left behind by a test would otherwise be read by the next one.
     */
    private fun resetSettings() {
        viewModel.setTheme(ThemeMode.DARK)
        viewModel.setHomeAlignment(HomeAlignment.CENTER)
        viewModel.setLabelCase(LabelCase.LOWERCASE)
        viewModel.setBackdropStyle(BackdropStyle.MATTE)
        viewModel.setUseCustomPhoto(false)
        viewModel.setShowCalendarGlance(true)
        viewModel.setPauseEnabled(false)
        viewModel.setReduceMotion(false)
        viewModel.setWindowEnabled(false)
        viewModel.setCalendarAsked(false)

        val pages = viewModel.state.value.settings.home.pages
        val keep = pages.firstOrNull { it.type == PageType.APPS } ?: pages.first()
        keep.config.itemKeys.toList().forEach { viewModel.toggleFavourite(it) }
        pages.filter { it.id != keep.id }.forEach { viewModel.removePage(it.id) }
        viewModel.onPagerSettled(0)
        viewModel.goToPage(0)
        viewModel.goHome()
        rule.waitForIdle()
    }

    /**
     * Waits for the first enumeration to finish. The drawer's definitive empty state is
     * only true once that has happened (P1-13), so tests that assert it wait for the flag
     * rather than racing the background scan.
     */
    private fun awaitEnumeration() {
        rule.waitUntil(5_000) { !viewModel.state.value.isLoading }
        rule.waitForIdle()
    }

    /**
     * Waits for the off-main-thread rescan that follows [FakeApps.install] to land in
     * `visibleApps`, which is what the drawers and rows resolve against.
     */
    private fun awaitVisibleApps(count: Int) {
        rule.waitUntil(5_000) { viewModel.state.value.visibleApps.size == count }
        rule.waitForIdle()
    }

    @Before
    fun setUp() = resetSettings()

    /** The toggleable row that contains [label] as one of its own cells. */
    private fun toggleLabelled(label: String) =
        rule.onNode(
            hasAnyDescendant(hasText(label)) and isToggleable(),
            useUnmergedTree = true,
        ).performScrollTo()

    /** The clock is "HH:mm" (24-hour) and the date is "EEEE, d MMMM". */
    private val clockPattern = Regex("""\b\d{1,2}:\d{2}\b""")
    private val datePattern = Regex("""\p{L}+,\s*\d{1,2}\s+\p{L}+""")

    // ---- Home: Quiet default & Calm Canvas -----------------------------------------

    @Test
    fun home_defaultsToQuietHome_withClockCalendarGlanceAndNoPageDots() {
        val settings = viewModel.state.value.settings
        assertEquals(HomeAlignment.CENTER, settings.homeAlignment)
        assertEquals(LabelCase.LOWERCASE, settings.labelCase)
        assertTrue(LauncherSettings().pauseEnabled)

        rule.onAllNodes(textMatching(clockPattern)).assertCountEquals(1)
        rule.onAllNodes(textMatching(datePattern)).assertCountEquals(1)
        rule.onNodeWithText("Tap to connect Google Calendar").assertIsDisplayed()
        rule.onNodeWithText("No apps on Home yet. Open All apps and pin the essentials.").assertIsDisplayed()
        rule.onNodeWithText("Search apps").assertIsDisplayed()
        rule.onAllNodesWithText("Edit pages").assertCountEquals(0)
    }

    @Test
    fun quietHome_showsPinnedAppsLowercaseByDefault_andSupportsOriginalAndLeftAlignment() {
        val keys = listOf(
            FakeApps.install(rule.activity, "com.example.textonly1", "Alpha"),
            FakeApps.install(rule.activity, "com.example.textonly2", "Beta"),
        )
        viewModel.refreshApps()
        awaitVisibleApps(2)
        keys.forEach { viewModel.toggleFavourite(it) }
        rule.waitForIdle()

        assertEquals(keys, viewModel.state.value.settings.home.page("p_apps")?.config?.itemKeys)
        val resolved = viewModel.state.value.favourites
        assertEquals(2, resolved.size)
        resolved.forEach {
            rule.onNodeWithText(it.label.lowercase()).performScrollTo().assertIsDisplayed()
        }
        rule.onAllNodesWithText("Unpin").assertCountEquals(0)

        viewModel.setLabelCase(LabelCase.ORIGINAL)
        viewModel.setHomeAlignment(HomeAlignment.LEFT)
        rule.waitForIdle()

        resolved.forEach {
            rule.onNodeWithText(it.label).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun appRow_respectsLowercaseToggle_andCenteredMode() {
        val target = LaunchTarget("com.instagram.android", ".Main", "Instagram")
        rule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                LauncherTheme(mode = ThemeMode.DARK) {
                    AppRow(
                        viewModel = viewModel,
                        target = target,
                        isFavourite = true,
                        onClick = {},
                        onToggleFavourite = {},
                        onToggleHidden = {},
                        lowercaseLabel = true,
                        centered = true,
                        showFavouriteDot = false,
                    )
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("instagram").assertIsDisplayed()
        rule.onAllNodesWithText("Instagram").assertCountEquals(0)
        rule.onAllNodesWithText("Unpin").assertCountEquals(0)
    }

    @Test
    fun searchPill_opensTheDrawer_andTheFocusAskIsConsumed() {
        rule.onNodeWithText("Search apps").assertIsDisplayed()

        rule.onNodeWithText("Search apps").performClick()
        rule.waitForIdle()

        assertEquals(Screen.APPS, viewModel.state.value.screen)
        assertFalse(viewModel.state.value.autoFocusSearch)
    }

    @Test
    fun settings_hasNoCalmCanvasSwitcher_andShowsAtmosphereAndPreview() {
        viewModel.openSettings()
        rule.waitForIdle()
        rule.onAllNodesWithText("Calm Canvas").assertCountEquals(0)
        rule.onAllNodesWithText("Home experience").assertCountEquals(0)
        rule.onNodeWithText("Atmosphere").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Window shadow").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Open screen usage").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Select detected social apps").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun usageScreen_withoutAccess_showsRestrictedSettingsAppInfoHelper() {
        rule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                LauncherTheme(mode = ThemeMode.DARK) {
                    UsageScreen(viewModel, viewModel.state.value.copy(usageAccess = false))
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("Grant usage access").assertIsDisplayed()
        rule.onNodeWithText("Open App Info").assertIsDisplayed()
    }

    // ---- Settings --------------------------------------------------------------------

    @Test
    fun settings_themeChoice_isWarmSageOrMatteCharcoal_andDefaultsToDark() {
        viewModel.openSettings()
        rule.waitForIdle()
        assertEquals(ThemeMode.DARK, viewModel.state.value.settings.theme)

        rule.onNodeWithText("Warm Sage").performClick()
        rule.waitForIdle()
        assertEquals(ThemeMode.LIGHT, viewModel.state.value.settings.theme)

        rule.onNodeWithText("Matte Charcoal").performClick()
        rule.waitForIdle()
        assertEquals(ThemeMode.DARK, viewModel.state.value.settings.theme)
    }

    /** System was a third state that only deferred the choice; it must not come back. */
    @Test
    fun settings_hasNoSystemThemeOption() {
        viewModel.openSettings()
        rule.waitForIdle()
        rule.onAllNodesWithText("System").assertCountEquals(0)
    }

    @Test
    fun settings_noLongerShowsLegacyBloatedRows() {
        viewModel.openSettings()
        rule.waitForIdle()
        listOf(
            "24-hour clock",
            "Pinned apps per page",
            "App icons",
            "Match wallpaper",
            "Import a Zendo snapshot",
            "Horizontal swipe pages Home",
            "Swipe up on bottom bar opens drawer",
        ).forEach { label ->
            rule.onAllNodesWithText(label).assertCountEquals(0)
        }
    }

    @Test
    fun settings_showsItsSections() {
        viewModel.openSettings()
        rule.waitForIdle()
        listOf(
            "Appearance",
            "Glance & Usage",
            "Apps",
            "Calendar",
            "Data",
            "Mindful Gate",
            "Home app",
            "About",
        ).forEach { label ->
            rule.onNodeWithText(label).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun settings_reduceTransitions_toggles() {
        viewModel.openSettings()
        rule.waitForIdle()
        assertFalse(viewModel.state.value.settings.reduceMotion)

        toggleLabelled("Reduce transitions").performClick()
        rule.waitForIdle()
        assertTrue(viewModel.state.value.settings.reduceMotion)
    }

    @Test
    fun aboutLine_reportsTheVersionTheBuildCarries() {
        viewModel.openSettings()
        rule.waitForIdle()
        rule.onNodeWithText(
            "Zendo Launcher ${BuildConfig.VERSION_NAME}, a standalone calm home screen.",
        ).performScrollTo().assertIsDisplayed()
    }

    // ---- Drawer ----------------------------------------------------------------------

    @Test
    fun allAppsAction_navigatesToAppList_andHomeReturns() {
        rule.onNodeWithText("All apps").performClick()
        rule.waitForIdle()
        assertEquals(Screen.APPS, viewModel.state.value.screen)

        rule.onNodeWithText("All apps").assertIsDisplayed()
        rule.onNodeWithText("Search").assertIsDisplayed()
        awaitEnumeration()
        rule.onNodeWithText("No launchable apps found.").assertIsDisplayed()

        rule.onNodeWithText("Home").performClick()
        rule.waitForIdle()
        assertEquals(Screen.HOME, viewModel.state.value.screen)
        rule.onNodeWithText("Search apps").assertIsDisplayed()
    }

    @Test
    fun search_clearAction_emptiesQuery_andStaysOnAppList() {
        viewModel.openApps()
        rule.waitForIdle()
        viewModel.setQuery("zzz")
        rule.waitForIdle()
        rule.onNodeWithText("Results").assertIsDisplayed()

        rule.onNodeWithText("Clear").performClick()
        rule.waitForIdle()
        assertEquals("", viewModel.state.value.query)
        assertEquals(Screen.APPS, viewModel.state.value.screen)
    }

    @Test
    fun search_clearAction_absentWhileQueryIsEmpty() {
        viewModel.openApps()
        rule.waitForIdle()
        rule.onAllNodesWithText("Clear").assertCountEquals(0)
    }

    /**
     * P1-13: "No launchable apps found." is a claim about the device, so it must not be
     * made while the first enumeration is still in flight.
     */
    @Test
    fun appList_whileEnumerationInFlight_showsProgressNotTheEmptyState() {
        rule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                LauncherTheme(mode = ThemeMode.DARK) {
                    AppListScreen(viewModel, UiState(isLoading = true))
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("Looking for apps…").assertIsDisplayed()
        rule.onAllNodesWithText("No launchable apps found.").assertCountEquals(0)
    }

    @Test
    fun appList_loadingFlag_clearsAfterEnumeration() {
        rule.waitUntil(5_000) { !viewModel.state.value.isLoading }
        viewModel.openApps()
        rule.waitForIdle()
        rule.onNodeWithText("No launchable apps found.").assertIsDisplayed()
    }

    @Test
    fun appList_letterBuckets_followTheSortsNormalisation() {
        assertEquals("A", letterOf("Ámbar"))
        assertEquals("Z", letterOf("Zendo"))
        assertEquals("#", letterOf("7-Zip"))
        assertEquals("#", letterOf("!Widget"))
    }

    // ---- Hide / pin ------------------------------------------------------------------

    @Test
    fun hideApp_offersUndo_andUndoPutsTheAppBack() {
        val key = "com.example.notes/.MainActivity"
        assertFalse(key in viewModel.state.value.settings.hidden)

        viewModel.toggleHidden(key)
        rule.waitForIdle()
        assertTrue(key in viewModel.state.value.settings.hidden)
        assertEquals(key, viewModel.state.value.undoHidden?.key)

        viewModel.undoHide()
        rule.waitForIdle()
        assertFalse(key in viewModel.state.value.settings.hidden)
        assertNull(viewModel.state.value.undoHidden)
    }

    @Test
    fun unhide_takesDownTheUndoOffer() {
        val key = "com.example.notes/.MainActivity"

        viewModel.toggleHidden(key)
        rule.waitForIdle()
        assertNotNull(viewModel.state.value.undoHidden)

        viewModel.toggleHidden(key)
        rule.waitForIdle()
        assertFalse(key in viewModel.state.value.settings.hidden)
        assertNull(viewModel.state.value.undoHidden)
    }

    /**
     * P0-4: pinning must not be reachable only through the long press. The row is built
     * with the activity's own view model, so this exercises the affordance as the drawer
     * composes it.
     */
    @Test
    fun appRow_showsPinAction_whenTheDrawerAsksForIt() {
        rule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                LauncherTheme(mode = ThemeMode.DARK) {
                    AppRow(
                        viewModel = viewModel,
                        target = LaunchTarget("com.example.notes", ".MainActivity", "Notes"),
                        isFavourite = false,
                        onClick = {},
                        onToggleFavourite = {},
                        onToggleHidden = {},
                        showPinAction = true,
                    )
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("Pin").assertIsDisplayed()
        // One repeated word down a list is indistinguishable to a screen reader, so the
        // action names the app it acts on.
        rule.onNodeWithContentDescription("Pin Notes").assertIsDisplayed()
    }

    /** The other half of P0-4: Home leaves the action off; the row is already on Home. */
    @Test
    fun appRow_hidesPinAction_onHomeRows() {
        rule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                LauncherTheme(mode = ThemeMode.DARK) {
                    AppRow(
                        viewModel = viewModel,
                        target = LaunchTarget("com.example.notes", ".MainActivity", "Notes"),
                        isFavourite = true,
                        onClick = {},
                        onToggleFavourite = {},
                        onToggleHidden = {},
                    )
                }
            }
        }
        rule.waitForIdle()
        rule.onAllNodesWithText("Pin").assertCountEquals(0)
        rule.onAllNodesWithText("Unpin").assertCountEquals(0)
        rule.onNodeWithText("Notes").assertIsDisplayed()
    }

    // ---- Back ------------------------------------------------------------------------

    @Test
    fun back_onPlainHome_claimsNothing() {
        assertFalse(viewModel.back())
        assertEquals(Screen.HOME, viewModel.state.value.screen)
        assertFalse(rule.activity.isFinishing)
    }
}
