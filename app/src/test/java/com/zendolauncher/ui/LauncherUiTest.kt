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
        viewModel.setPauseEnabled(false)
        viewModel.setReduceMotion(false)
        viewModel.setWindowEnabled(false)
        viewModel.setCalendarAsked(false)

        val pages = viewModel.state.value.settings.home.pages
        val keep = pages.firstOrNull { it.type == PageType.APPS } ?: pages.first()
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

    // ---- Home: one page, text only ---------------------------------------------------

    @Test
    fun home_startsWithASingleAppsPage_andNoPageDots() {
        val home = viewModel.state.value.settings.home
        assertEquals(1, home.pages.size)
        assertEquals(PageType.APPS, home.pages.single().type)

        // One page means nothing to indicate and nothing to swipe to. The page's own name
        // is a heading, not a dot: it appears once, at the top of the list.
        rule.onAllNodesWithText("Apps").assertCountEquals(1)
        rule.onNodeWithText("No apps on this page yet. Open All apps and pin the ones you want.").assertIsDisplayed()
        rule.onNodeWithText("Search apps").assertIsDisplayed()
    }

    @Test
    fun home_hasNoClockOnTheAppsPage() {
        // The clock belongs to the Today page. On a fresh install there is no Today page,
        // so the first screen is the list and nothing else.
        rule.onAllNodes(textMatching(clockPattern)).assertCountEquals(0)
    }

    @Test
    fun home_showsPinnedAppsAsText() {
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
        // Rendered with the label the launcher resolved, as text — no icon slot at all.
        resolved.forEach { rule.onNodeWithText(it.label).assertIsDisplayed() }
    }

    @Test
    fun searchPill_opensTheDrawer_andTheFocusAskIsConsumed() {
        rule.onNodeWithText("Search apps").assertIsDisplayed()

        rule.onNodeWithText("Search apps").performClick()
        rule.waitForIdle()

        assertEquals(Screen.APPS, viewModel.state.value.screen)
        assertFalse(viewModel.state.value.autoFocusSearch)
    }

    // ---- Adding a page ---------------------------------------------------------------

    @Test
    fun editPages_addPage_offersTypes_andCreatesTheChosenOne() {
        viewModel.enterPageEdit()
        rule.waitForIdle()
        rule.onNodeWithText("Add page").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Add a page").assertIsDisplayed()
        rule.onNodeWithText("Today").performClick()
        rule.waitForIdle()

        val pages = viewModel.state.value.settings.home.pages
        assertEquals(2, pages.size)
        assertEquals(PageType.TODAY, pages.last().type)
        assertFalse(viewModel.state.value.addingPage)

        viewModel.leavePageEdit()
        viewModel.removePage(pages.last().id)
        rule.waitForIdle()
    }

    @Test
    fun addPage_back_closesThePickerWithoutAddingAPage() {
        val before = viewModel.state.value.settings.home.pages.size
        viewModel.enterPageEdit()
        viewModel.openAddPage()
        rule.waitForIdle()
        assertTrue(viewModel.state.value.addingPage)

        assertTrue(viewModel.back())
        rule.waitForIdle()
        assertFalse(viewModel.state.value.addingPage)
        assertEquals(before, viewModel.state.value.settings.home.pages.size)
        viewModel.leavePageEdit()
    }

    /** The Today page exists only when it was added: clock, date and the local dashboard. */
    @Test
    fun todayPage_rendersClockAndDashboard_onlyWhenAdded() {
        rule.onAllNodesWithText("Highlights").assertCountEquals(0)

        viewModel.addPage(PageType.TODAY)
        rule.waitForIdle()

        rule.onAllNodes(textMatching(clockPattern)).assertCountEquals(1)
        rule.onAllNodes(textMatching(datePattern)).assertCountEquals(1)
        rule.onNodeWithText("Name the one thing that matters today.").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Highlights").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("No tasks yet. Add the first one below.").performScrollTo().assertIsDisplayed()

        val today = viewModel.state.value.settings.home.pages.last()
        viewModel.removePage(today.id)
        rule.waitForIdle()
    }

    /**
     * The Plan page renders the date navigation and the calendar's permission state.
     * Robolectric has no calendar permission, so the page must say so instead of drawing
     * an empty agenda.
     */
    @Test
    fun planPage_rendersAgendaStates_andDateNavigation() {
        viewModel.addPage(PageType.PLAN)
        rule.waitForIdle()

        rule.onNodeWithText("Plan").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Earlier").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Later").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Allow calendar access").performScrollTo().assertIsDisplayed()

        val plan = viewModel.state.value.settings.home.pages.last()
        viewModel.removePage(plan.id)
        rule.waitForIdle()
    }

    // ---- Settings --------------------------------------------------------------------

    @Test
    fun settings_themeChoice_isLightOrDark_andDefaultsToDark() {
        viewModel.openSettings()
        rule.waitForIdle()
        assertEquals(ThemeMode.DARK, viewModel.state.value.settings.theme)

        rule.onNodeWithText("Light").performClick()
        rule.waitForIdle()
        assertEquals(ThemeMode.LIGHT, viewModel.state.value.settings.theme)

        rule.onNodeWithText("Dark").performClick()
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

    /**
     * The rows that were removed: pin limits and order, 24-hour clock, icon style, layout
     * mode, wallpaper gallery, screen time, Zendo import, gesture toggles. Each was a
     * setting for something a person either never changes or changes elsewhere.
     */
    @Test
    fun settings_noLongerShowsTheRowsThatWereRemoved() {
        viewModel.openSettings()
        rule.waitForIdle()
        listOf(
            "24-hour clock",
            "Pinned apps per page",
            "App icons",
            "Layout",
            "Match wallpaper",
            "Open wallpaper gallery",
            "Screen time summary",
            "Import a Zendo snapshot",
            "Horizontal swipe pages Home",
            "Swipe up on bottom bar opens drawer",
            "Show today",
        ).forEach { label ->
            rule.onAllNodesWithText(label).assertCountEquals(0)
        }
    }

    @Test
    fun settings_showsItsRemainingSections() {
        viewModel.openSettings()
        rule.waitForIdle()
        listOf("Appearance", "Apps", "Calendar", "Data", "Pause", "Home app", "About").forEach { label ->
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

    // ---- Edit mode -------------------------------------------------------------------

    /**
     * Renames and reorders are written to the store, so this test restores the layout
     * afterwards: it lives in SharedPreferences, which Robolectric keeps for the whole JVM.
     */
    @Test
    fun editMode_renamesAndReordersPages_andPutsThemBack() {
        val startIds = viewModel.state.value.settings.home.pages.map { it.id }
        val startNames = viewModel.state.value.settings.home.pages.associate { it.id to it.name }
        val pageId = startIds.first()

        viewModel.enterPageEdit()
        rule.waitForIdle()
        // The bar's controls are the only way out of editing, so the name is not enough:
        // it has to be a control that is actually there.
        rule.onNodeWithText("Done").assertIsDisplayed()

        viewModel.renamePage(pageId, "Deep work")
        rule.waitForIdle()
        assertEquals("Deep work", viewModel.state.value.settings.home.page(pageId)?.name)
        viewModel.renamePage(pageId, startNames.getValue(pageId))
        rule.waitForIdle()

        // One page cannot be reordered, so add a second one, move it, then remove it.
        viewModel.addPage(PageType.NOTES)
        rule.waitForIdle()
        val added = viewModel.state.value.settings.home.pages.last().id
        assertEquals(1, viewModel.state.value.settings.home.pages.indexOfFirst { it.id == added })

        viewModel.movePage(added, -1)
        rule.waitForIdle()
        assertEquals(0, viewModel.state.value.settings.home.pages.indexOfFirst { it.id == added })

        viewModel.removePage(added)
        rule.waitForIdle()
        assertEquals(startIds, viewModel.state.value.settings.home.pages.map { it.id })
        assertEquals(
            startNames,
            viewModel.state.value.settings.home.pages.associate { it.id to it.name },
        )

        viewModel.leavePageEdit()
        rule.waitForIdle()
        assertFalse(viewModel.state.value.pageEditing)
    }
}
