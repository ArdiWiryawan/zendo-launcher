package com.zendolauncher.ui

import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zendolauncher.BuildConfig
import com.zendolauncher.LauncherActivity
import com.zendolauncher.core.HomeLayout
import com.zendolauncher.data.LayoutMode
import com.zendolauncher.data.ThemeMode
import com.zendolauncher.model.LaunchTarget
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
        viewModel.setPauseEnabled(false)
        // Home is a pager and the active page is persisted, so a test that swiped away would
        // otherwise leave the next one looking at whatever page it stopped on. The stored id
        // is written first, then the pager is moved, because the move is what the effect
        // acts on. Page 0 is the Today page, which is what the default layout starts on.
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
        rule.onNodeWithText("Highlights").performScrollTo().assertIsDisplayed()
        rule.onAllNodes(textMatching(clockPattern)).assertCountEquals(1)
        rule.onAllNodes(textMatching(datePattern)).assertCountEquals(1)
    }

    @Test
    fun homeScreen_showsTodayEmptyStates_andBottomActions() {
        // Today dashboard is local and persistent: highlights, tasks and upcoming each
        // render an honest empty state rather than invented rows.
        rule.onNodeWithText("Write your priority here. It stays until you edit or archive it.").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("No tasks yet. Add the first one below.").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Calendar access is off. Upcoming stays empty until you allow it.").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("All apps").assertIsDisplayed()
        rule.onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test
    fun allAppsAction_navigatesToAppList_andHomeReturns() {
        rule.onNodeWithText("All apps").performClick()
        rule.waitForIdle()
        assertEquals(Screen.APPS, viewModel.state.value.screen)

        // The screen header, the search field with its placeholder, and the definitive
        // empty state, which is only true once the enumeration has finished.
        rule.onNodeWithText("All apps").assertIsDisplayed()
        rule.onNodeWithText("Search").assertIsDisplayed()
        awaitEnumeration()
        rule.onNodeWithText("No launchable apps found.").assertIsDisplayed()

        rule.onNodeWithText("Home").performClick()
        rule.waitForIdle()
        assertEquals(Screen.HOME, viewModel.state.value.screen)
        rule.onNodeWithText("Highlights").assertIsDisplayed()
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

    /** Clearing must not require leaving the drawer, which goHome() was the only route to. */
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

    /** The clear affordance has nothing to clear until something is typed. */
    @Test
    fun search_clearAction_absentWhileQueryIsEmpty() {
        viewModel.openApps()
        rule.waitForIdle()
        rule.onAllNodesWithText("Clear").assertCountEquals(0)
    }

    @Test
    fun todayBlock_absentWithDefaultUnavailableSource_andScreenStillRenders() {
        // Defaults: todayEnabled = true, zendoEnabled = false. The Today dashboard is
        // local now (highlights/tasks/calendar), so Zendo state cannot change it.
        // The clock is the page's own and is unaffected by any of it.
        assertTrue(viewModel.state.value.settings.todayEnabled)
        assertFalse(viewModel.state.value.settings.zendoEnabled)
        rule.onNodeWithText("Highlights").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Write your priority here. It stays until you edit or archive it.").performScrollTo().assertIsDisplayed()
        rule.onAllNodes(textMatching(clockPattern)).assertCountEquals(1)

        // Turning the Zendo source on cannot produce content: the only shipped
        // source is UnavailableZendoSource, which always returns null. The local
        // dashboard is unchanged.
        viewModel.setZendoEnabled(true)
        rule.waitForIdle()
        rule.onNodeWithText("Write your priority here. It stays until you edit or archive it.").performScrollTo().assertIsDisplayed()
        rule.onAllNodes(textMatching(clockPattern)).assertCountEquals(1)

        // Turning the section off removes the dashboard entirely, and the page still
        // renders its clock and its date.
        viewModel.setTodayEnabled(false)
        rule.waitForIdle()
        rule.onAllNodesWithText("Highlights").assertCountEquals(0)
        rule.onAllNodesWithText("Write your priority here. It stays until you edit or archive it.").assertCountEquals(0)
        rule.onAllNodes(textMatching(clockPattern)).assertCountEquals(1)
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

    /**
     * Refresh is not a Home concern: both repositories rescan through
     * `LauncherApps.Callback` and the one manual control lives on the app list, so its
     * presence must not depend on the icon layout either.
     */
    @Test
    fun homeScreen_hasNoRefreshActionInAnyLayout() {
        rule.onAllNodesWithText("Refresh").assertCountEquals(0)
        viewModel.setLayout(LayoutMode.ICON_TEXT)
        rule.waitForIdle()
        rule.onAllNodesWithText("Refresh").assertCountEquals(0)
    }

    @Test
    fun settings_showsEmptyPinnedAndHiddenSections() {
        viewModel.openSettings()
        rule.waitForIdle()
        rule.onNodeWithText("No pinned apps.").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("No hidden apps.").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("About").performScrollTo().assertIsDisplayed()
    }

    /**
     * The About line shipped a hardcoded "0.2.0" and went stale the first time the version
     * was raised, so a user on 0.2.1 was told they were on 0.2.0. It now reads the version
     * the build actually carries; this asserts the two cannot drift apart again.
     */
    @Test
    fun aboutLine_reportsTheVersionTheBuildCarries() {
        viewModel.openSettings()
        rule.waitForIdle()
        rule.onNodeWithText(
            "Zendo Launcher ${BuildConfig.VERSION_NAME}, a standalone monochrome home screen.",
        ).performScrollTo().assertIsDisplayed()
    }

    /**
     * The drawer's headers come from the same normalisation as the sort, so an accented
     * name files under the base letter it was ordered by. Everything that is not a
     * letter shares one bucket instead of each getting its own header.
     */
    @Test
    fun appList_letterBuckets_followTheSortsNormalisation() {
        assertEquals("A", letterOf("Ámbar"))
        assertEquals("Z", letterOf("Zendo"))
        assertEquals("#", letterOf("7-Zip"))
        assertEquals("#", letterOf("!Widget"))
    }

    /**
     * P1-13: "No launchable apps found." is a claim about the device, so it must not be
     * made while the first enumeration is still in flight. That window is a few hundred
     * milliseconds at launch, which a test cannot reliably observe, so the loading state
     * is rendered directly instead of raced.
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

    /**
     * The other half of P1-13: a loading flag that never clears is a stuck spinner on a
     * home screen, so the enumeration must always end, including when it throws.
     */
    @Test
    fun appList_loadingFlag_clearsAfterEnumeration() {
        rule.waitUntil(5_000) { !viewModel.state.value.isLoading }
        viewModel.openApps()
        rule.waitForIdle()
        rule.onNodeWithText("No launchable apps found.").assertIsDisplayed()
    }

    /**
     * P1-11: hiding has no confirmation step, so the undo offer is the only way back.
     * Robolectric discovers no apps, so no row exists to hide through the UI; the view
     * model is driven directly, which is where the undo state actually lives.
     */
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

    /** Unhiding is its own undo, so the offer must not outlive it. */
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
     * composes it; the drawer itself is unusable here because Robolectric discovers no
     * apps. Every callback is a no-op, because the test is about what the row shows.
     */
    @Test
    fun appRow_showsPinAction_whenTheDrawerAsksForIt() {
        rule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                LauncherTheme(mode = ThemeMode.DARK) {
                    AppRow(
                        viewModel = viewModel,
                        target = LaunchTarget("com.example.notes", ".MainActivity", "Notes"),
                        layout = LayoutMode.TEXT,
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

    /**
     * The Back contract for Home, in the order the user experiences it. Edit mode is the
     * only layer Home has, and Back leaves it one stage at a time; the `false` return is
     * what makes the last case true, because it means nothing of ours handled the press.
     */
    @Test
    fun back_leavesEditMode_andPlainHomeClaimsNothing() {
        assertFalse(viewModel.back())
        assertEquals(Screen.HOME, viewModel.state.value.screen)

        viewModel.enterPageEdit()
        rule.waitForIdle()
        assertTrue(viewModel.state.value.pageEditing)

        assertTrue(viewModel.back())
        rule.waitForIdle()
        assertFalse(viewModel.state.value.pageEditing)
        assertEquals(Screen.HOME, viewModel.state.value.screen)

        // Nothing open: Back must not be claimed. The activity is untouched too, so this is
        // not a case of the view model answering for a handler that would have exited.
        assertFalse(viewModel.back())
        assertFalse(rule.activity.isFinishing)
    }

    /**
     * A page that is not the active one still resolves its own rows, and every page renders
     * something true: the Focus page's unbuilt sections say so in words.
     */
    @Test
    fun focusPage_rendersHonestEmptyStates() {
        viewModel.goToPage(2)
        rule.waitForIdle()
        assertEquals("p_focus", viewModel.state.value.settings.home.activePageId)

        rule.onNodeWithText("Focus session").assertIsDisplayed()
        rule.onNodeWithText("Sessions are not built yet. Nothing here runs a timer or blocks apps.")
            .performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Usage summary is not built yet. This app does not read usage statistics.")
            .performScrollTo().assertIsDisplayed()
    }

    /**
     * The Launch page keeps the apps it was migrated with. Robolectric finds no installed
     * apps, so no row can be resolved; what must hold is that the page exists, is reachable
     * and states its own emptiness rather than borrowing another page's.
     */
    @Test
    fun launchPage_showsItsOwnEmptyState() {
        viewModel.goToPage(1)
        rule.waitForIdle()
        assertEquals("p_launch", viewModel.state.value.settings.home.activePageId)

        rule.onNodeWithText("Essentials").assertIsDisplayed()
        rule.onNodeWithText("No pinned apps here yet. Open All apps and pin the ones you want.")
            .performScrollTo().assertIsDisplayed()
    }

    /**
     * Renames and reorders are written to the store, so this test puts back what it changes:
     * the layout lives in SharedPreferences, which Robolectric keeps for the whole JVM, and a
     * page left renamed or out of order would be read by whichever test runs next. The
     * last-page-removal rule is a pure model rule and is covered there instead.
     */
    @Test
    fun editMode_renamesAndReordersPages_andPutsThemBack() {
        viewModel.enterPageEdit()
        rule.waitForIdle()
        // The bar's controls are the only way out of editing, so the name is not enough:
        // it has to be a control that is actually there.
        rule.onNodeWithText("Done").assertIsDisplayed()

        viewModel.renamePage("p_focus", "Deep work")
        rule.waitForIdle()
        assertEquals("Deep work", viewModel.state.value.settings.home.page("p_focus")?.name)
        viewModel.renamePage("p_focus", "Focus")
        rule.waitForIdle()

        viewModel.movePage("p_focus", -1)
        rule.waitForIdle()
        assertEquals(
            listOf("p_today", "p_focus", "p_launch"),
            viewModel.state.value.settings.home.pages.map { it.id },
        )
        viewModel.movePage("p_focus", 2)
        rule.waitForIdle()
        assertEquals(
            HomeLayout.DEFAULT.pages.map { it.id },
            viewModel.state.value.settings.home.pages.map { it.id },
        )

        viewModel.leavePageEdit()
        rule.waitForIdle()
        assertFalse(viewModel.state.value.pageEditing)
    }

    /**
     * The other half of P0-4: Home leaves the action off. Every row there is already
     * pinned, and the dot already reports that, so an action on each row is only noise.
     */
    @Test
    fun appRow_hidesPinAction_onHomeRows() {
        rule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                LauncherTheme(mode = ThemeMode.DARK) {
                    AppRow(
                        viewModel = viewModel,
                        target = LaunchTarget("com.example.notes", ".MainActivity", "Notes"),
                        layout = LayoutMode.TEXT,
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
}
