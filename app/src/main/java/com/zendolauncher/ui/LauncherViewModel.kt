package com.zendolauncher.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zendolauncher.core.AppQuery
import com.zendolauncher.core.Highlight
import com.zendolauncher.core.TaskItem
import com.zendolauncher.core.WallpaperTemplates
import com.zendolauncher.data.FocusNoteStore
import com.zendolauncher.data.HighlightStore
import com.zendolauncher.data.LayoutMode
import com.zendolauncher.data.LauncherSettings
import com.zendolauncher.data.SettingsStore
import com.zendolauncher.data.TaskStore
import com.zendolauncher.data.ThemeMode
import com.zendolauncher.data.WallpaperStore
import com.zendolauncher.data.WallpaperTarget
import com.zendolauncher.model.CalendarEvent
import com.zendolauncher.model.DeviceCalendar
import com.zendolauncher.model.LaunchTarget
import com.zendolauncher.model.ZendoToday
import com.zendolauncher.platform.AppLauncher
import com.zendolauncher.platform.AppRepository
import com.zendolauncher.platform.CalendarRepository
import com.zendolauncher.platform.ShortcutRepository
import com.zendolauncher.platform.UnavailableZendoSource
import com.zendolauncher.platform.WallpaperApplier
import com.zendolauncher.platform.WallpaperApplyResult
import com.zendolauncher.platform.ZendoSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

enum class Screen { ONBOARDING, HOME, APPS, SETTINGS, WALLPAPERS, CALENDAR, HIGHLIGHT_EDITOR, TASK_EDITOR }

data class UiState(
    val screen: Screen = Screen.HOME,
    val query: String = "",
    val settings: LauncherSettings = LauncherSettings(),
    val favourites: List<LaunchTarget> = emptyList(),
    val results: List<LaunchTarget> = emptyList(),
    val visibleApps: List<LaunchTarget> = emptyList(),
    val shortcutAccess: Boolean = false,
    val isDefaultHome: Boolean = false,
    val zendo: ZendoToday? = null,
    /**
     * Every discovered target, hidden apps included. [visibleApps] has already dropped
     * the hidden ones, so a screen that has to name a hidden app has nothing left to
     * name it by unless the unfiltered list is carried alongside.
     */
    val allApps: List<LaunchTarget> = emptyList(),
    /**
     * The app just hidden, while it can still be put back. Non-null only for the few
     * seconds the undo note is on screen; hiding has no confirmation step, so this is
     * the only way back.
     */
    val undoHidden: LaunchTarget? = null,
    /** True only while the first enumeration is still running; see [LauncherViewModel.onStart]. */
    val isLoading: Boolean = false,
    /**
     * The distraction-listed app waiting on the intentional pause. Non-null only while
     * the pause dialog is up; launching it is the only way out besides cancelling.
     */
    val pendingPause: LaunchTarget? = null,
    /**
     * Whether Home is arranging its pages rather than using them.
     *
     * A boolean rather than an enum because there is exactly one thing that can be edited:
     * the set of pages. A *page's contents* are already editable from Settings, where the
     * pinned list lives, so a second per-page editor here would be a second surface for
     * the same operation. This is the whole state edit mode needs.
     */
    val pageEditing: Boolean = false,
    /**
     * The page the pager should move to, or null when there is nowhere to go. Cleared by
     * the view once it has arrived, so a later recomposition cannot drag the user back to a
     * page they have since swiped away from.
     */
    val pagerTargetPage: Int? = null,
    /** The Focus page's one line. Empty is the normal state, not a failure. */
    val focusNote: String = "",
    // ---- Today dashboard (local, persistent) -----------------------------------
    val highlights: List<Highlight> = emptyList(),
    val tasks: List<TaskItem> = emptyList(),
    // ---- Calendar (device provider, read-only) ---------------------------------
    val calendars: List<DeviceCalendar> = emptyList(),
    val calendarEvents: List<CalendarEvent> = emptyList(),
    val calendarPermission: Boolean = false,
    val selectedDateEpochDay: Long = LocalDate.now().toEpochDay(),
    // ---- Wallpaper ------------------------------------------------------------
    val wallpaperSelectedId: String? = null,
    val wallpaperTarget: WallpaperTarget = WallpaperTarget.BOTH,
    val wallpaperApplying: Boolean = false,
    val wallpaperMessage: String? = null,
    // ---- Overlays and editors (ephemeral) ----------------------------------------
    val editingHighlightId: String? = null,
    /** Draft title in the full-screen highlight editor. Lives here so rotation keeps it. */
    val highlightTitle: String = "",
    /** Draft body in the full-screen highlight editor. */
    val highlightNote: String = "",
    /** Whether the draft wants primary status. */
    val highlightPin: Boolean = false,
    /** Validation message in the highlight editor, or null. */
    val highlightError: String? = null,
    /** Discard-unsaved-changes confirm over the highlight editor. */
    val showDiscardHighlight: Boolean = false,
    /** Id awaiting delete confirmation in the highlights list, or null. */
    val confirmDeleteHighlightId: String? = null,
    val showAllHighlights: Boolean = false,
    val showAllTasks: Boolean = false,
    val editingTaskId: String? = null,
    /** Draft fields in the full-screen task editor. */
    val taskTitle: String = "",
    val taskDueMillis: Long? = null,
    val taskNotes: String = "",
    val taskEditorError: String? = null,
    val showDiscardTask: Boolean = false,
    val confirmDeleteTaskId: String? = null,
)

/** The four app lists, grouped only so [`lists`]'s `combine` stays within its arity. */
private data class AppLists(
    val favourites: List<LaunchTarget>,
    val results: List<LaunchTarget>,
    val visibleApps: List<LaunchTarget>,
    val allApps: List<LaunchTarget>,
)

private data class Lists(
    val apps: AppLists,
    val screen: Screen,
    val pageEditing: Boolean,
    val pagerTargetPage: Int?,
)

private data class Prefs(
    val settings: LauncherSettings,
    val query: String,
    val shortcutAccess: Boolean,
    val isDefaultHome: Boolean,
    val focusNote: String,
)

@OptIn(ExperimentalCoroutinesApi::class)
class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val appRepository = AppRepository(application)
    private val shortcutRepository = ShortcutRepository(application)
    private val settingsStore = SettingsStore(application)
    private val focusNoteStore = FocusNoteStore(application)
    private val highlightStore = HighlightStore(application)
    private val taskStore = TaskStore(application)
    private val wallpaperStore = WallpaperStore(application)
    private val calendarRepository = CalendarRepository(application)
    private val appLauncher = AppLauncher(application)

    /** Swap this for a Supabase-backed implementation once the backend allows it. */
    private val zendoSource: ZendoSource = UnavailableZendoSource

    private val _screen = MutableStateFlow(if (settingsStore.read().onboardingSeen) Screen.HOME else Screen.ONBOARDING)
    private val _query = MutableStateFlow("")
    private val _isDefaultHome = MutableStateFlow(false)

    /**
     * Starts true so the very first frame of the drawer already reads as "in progress":
     * the enumeration below cannot have finished before the state flow is first read.
     * The default [UiState] leaves it false, which is what a preview or a test that
     * never calls [onStart] should see.
     */
    private val _isLoading = MutableStateFlow(true)

    /**
     * The app the undo note names. Mirrors [_isLoading]: a plain state flow folded into
     * the combine below, so the note appears and disappears with the rest of the state
     * instead of through a second observation path.
     */
    private val _undoHidden = MutableStateFlow<LaunchTarget?>(null)

    /** The auto-dismiss for [_undoHidden]; cancelling it is how a new hide takes over. */
    private var undoJob: Job? = null

    /**
     * The distraction-listed app waiting on the intentional pause. Plain dialog state:
     * set by [launch], cleared by [confirmPause]/[cancelPause]. No journaling.
     */
    private val _pendingPause = MutableStateFlow<LaunchTarget?>(null)

    /**
     * Home's own view state. Plain view state, not preferences: leaving edit mode is not a
     * setting the user made, so nothing here is written anywhere and a relaunch starts in
     * browsing.
     */
    private val _pageEditing = MutableStateFlow(false)
    private val _pagerTargetPage = MutableStateFlow<Int?>(null)

    // ---- Today / calendar / wallpaper (local, ephemeral UI state is here) ------------
    private val _selectedDate = MutableStateFlow(LocalDate.now())
    private val _calendars = MutableStateFlow<List<DeviceCalendar>>(emptyList())
    private val _calendarEvents = MutableStateFlow<List<CalendarEvent>>(emptyList())
    private val _calendarPermission = MutableStateFlow(false)
    private val _wallpaperApplying = MutableStateFlow(false)
    private val _wallpaperMessage = MutableStateFlow<String?>(null)
    // Highlight editor drafts live here (not in the composable) so rotation and a
    // transient interruption keep the user's text.
    private val _editingHighlightId = MutableStateFlow<String?>(null)
    private val _highlightTitle = MutableStateFlow("")
    private val _highlightNote = MutableStateFlow("")
    private val _highlightPin = MutableStateFlow(false)
    private val _highlightError = MutableStateFlow<String?>(null)
    private val _showDiscardHighlight = MutableStateFlow(false)
    private val _confirmDeleteHighlight = MutableStateFlow<String?>(null)
    private val _showAllHighlights = MutableStateFlow(false)
    private val _showAllTasks = MutableStateFlow(false)
    // Task editor drafts, same reason.
    private val _editingTaskId = MutableStateFlow<String?>(null)
    private val _taskTitle = MutableStateFlow("")
    private val _taskDue = MutableStateFlow<Long?>(null)
    private val _taskNotes = MutableStateFlow("")
    private val _taskError = MutableStateFlow<String?>(null)
    private val _showDiscardTask = MutableStateFlow(false)
    private val _confirmDeleteTask = MutableStateFlow<String?>(null)

    /** Where Back returns from Settings; lets Back-to-drawer keep the drawer query. */
    private var settingsReturn: Screen = Screen.HOME

    /** Drawer scroll position, retained across re-entry; see AppListScreen. */
    var drawerIndex: Int = 0
    var drawerOffset: Int = 0

    private val allApps: Flow<List<LaunchTarget>> =
        combine(appRepository.apps, shortcutRepository.shortcuts) { installed, shortcuts ->
            installed + shortcuts
        }

    private val visibleApps: Flow<List<LaunchTarget>> =
        combine(allApps, settingsStore.settings) { apps, settings ->
            AppQuery.visible(apps, settings.hidden)
        }

    /**
     * The active page's own app list, resolved against everything visible. This is what
     * Home shows, so a page's contents are its config and nothing else — the flat legacy
     * favourites list is never consulted here.
     */
    private val favourites: Flow<List<LaunchTarget>> =
        combine(visibleApps, settingsStore.settings) { apps, settings ->
            val layout = settings.home
            val active = layout.page(layout.activePageId ?: "") ?: layout.pages.firstOrNull()
            AppQuery.favourites(active?.config?.itemKeys.orEmpty(), apps)
        }

    private val results: Flow<List<LaunchTarget>> =
        combine(visibleApps, _query) { apps, query -> AppQuery.search(apps, query) }

    private val zendo: Flow<ZendoToday?> = settingsStore.settings
        .map { it.zendoEnabled }
        .distinctUntilChanged()
        .flatMapLatest { enabled ->
            if (enabled) flow { emit(zendoSource.load()) } else flowOf(null)
        }

    /**
     * The four app lists, grouped only so the `combine` below stays within its typed
     * arity. Nothing else reads this.
     */
    private val appLists: Flow<AppLists> =
        combine(favourites, results, visibleApps, allApps) { favourites, results, visibleApps, allApps ->
            AppLists(favourites, results, visibleApps, allApps)
        }

    private val lists: Flow<Lists> =
        combine(appLists, _screen, _pageEditing, _pagerTargetPage) { apps, screen, pageEditing, pagerTarget ->
            Lists(apps, screen, pageEditing, pagerTarget)
        }

    private val prefs: Flow<Prefs> =
        combine(settingsStore.settings, _query, shortcutRepository.available, _isDefaultHome, focusNoteStore.note) {
                settings, query, shortcutAccess, isDefaultHome, focusNote ->
            Prefs(settings, query, shortcutAccess, isDefaultHome, focusNote)
        }

    private val base: Flow<UiState> =
        combine(lists, prefs, zendo, _isLoading, _undoHidden) { lists, prefs, zendo, isLoading, undoHidden ->
            UiState(
                screen = lists.screen,
                query = prefs.query,
                settings = prefs.settings,
                favourites = lists.apps.favourites,
                results = lists.apps.results,
                visibleApps = lists.apps.visibleApps,
                shortcutAccess = prefs.shortcutAccess,
                isDefaultHome = prefs.isDefaultHome,
                zendo = zendo,
                allApps = lists.apps.allApps,
                undoHidden = undoHidden,
                isLoading = isLoading,
                pageEditing = lists.pageEditing,
                pagerTargetPage = lists.pagerTargetPage,
                focusNote = prefs.focusNote,
            )
        }

    private data class TodayBundle(
        val highlights: List<Highlight>,
        val tasks: List<TaskItem>,
        val calendars: List<DeviceCalendar>,
        val events: List<CalendarEvent>,
        val permission: Boolean,
        val date: LocalDate,
        val wallpaperId: String?,
        val wallpaperTarget: WallpaperTarget,
        val applying: Boolean,
        val message: String?,
        val editingId: String?,
        val draftTitle: String,
        val draftNote: String,
        val draftPin: Boolean,
        val draftError: String?,
        val showDiscardH: Boolean,
        val confirmDelH: String?,
        val showAllH: Boolean,
        val showAllT: Boolean,
        val editingTask: String?,
        val taskTitle: String,
        val taskDue: Long?,
        val taskNotes: String,
        val taskError: String?,
        val showDiscardT: Boolean,
        val confirmDelT: String?,
    )

    private val today: Flow<TodayBundle> = combine(
        highlightStore.highlights,
        taskStore.tasks,
        _calendars,
        _calendarEvents,
        _calendarPermission,
        _selectedDate,
        wallpaperStore.selectedId,
        wallpaperStore.target,
        _wallpaperApplying,
        _wallpaperMessage,
        _editingHighlightId,
        _highlightTitle,
        _highlightNote,
        _highlightPin,
        _highlightError,
        _showDiscardHighlight,
        _confirmDeleteHighlight,
        _showAllHighlights,
        _showAllTasks,
        _editingTaskId,
        _taskTitle,
        _taskDue,
        _taskNotes,
        _taskError,
        _showDiscardTask,
        _confirmDeleteTask,
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        TodayBundle(
            highlights = args[0] as List<Highlight>,
            tasks = args[1] as List<TaskItem>,
            calendars = args[2] as List<DeviceCalendar>,
            events = args[3] as List<CalendarEvent>,
            permission = args[4] as Boolean,
            date = args[5] as LocalDate,
            wallpaperId = args[6] as String?,
            wallpaperTarget = args[7] as WallpaperTarget,
            applying = args[8] as Boolean,
            message = args[9] as String?,
            editingId = args[10] as String?,
            draftTitle = args[11] as String,
            draftNote = args[12] as String,
            draftPin = args[13] as Boolean,
            draftError = args[14] as String?,
            showDiscardH = args[15] as Boolean,
            confirmDelH = args[16] as String?,
            showAllH = args[17] as Boolean,
            showAllT = args[18] as Boolean,
            editingTask = args[19] as String?,
            taskTitle = args[20] as String,
            taskDue = args[21] as Long?,
            taskNotes = args[22] as String,
            taskError = args[23] as String?,
            showDiscardT = args[24] as Boolean,
            confirmDelT = args[25] as String?,
        )
    }

    val state: StateFlow<UiState> =
        combine(base, _pendingPause, today) { base, pendingPause, t ->
            base.copy(
                pendingPause = pendingPause,
                highlights = t.highlights,
                tasks = t.tasks,
                calendars = t.calendars,
                calendarEvents = t.events,
                calendarPermission = t.permission,
                selectedDateEpochDay = t.date.toEpochDay(),
                wallpaperSelectedId = t.wallpaperId,
                wallpaperTarget = t.wallpaperTarget,
                wallpaperApplying = t.applying,
                wallpaperMessage = t.message,
                editingHighlightId = t.editingId,
                highlightTitle = t.draftTitle,
                highlightNote = t.draftNote,
                highlightPin = t.draftPin,
                highlightError = t.draftError,
                showDiscardHighlight = t.showDiscardH,
                confirmDeleteHighlightId = t.confirmDelH,
                showAllHighlights = t.showAllH,
                showAllTasks = t.showAllT,
                editingTaskId = t.editingTask,
                taskTitle = t.taskTitle,
                taskDueMillis = t.taskDue,
                taskNotes = t.taskNotes,
                taskEditorError = t.taskError,
                showDiscardTask = t.showDiscardT,
                confirmDeleteTaskId = t.confirmDelT,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            // Mirrors _screen's construction so the very first frame already shows the
            // right screen; a default UiState() would flash Home before onboarding.
            UiState(
                screen = _screen.value,
                settings = settingsStore.settings.value,
                isLoading = _isLoading.value,
            ),
        )

    private var enumerated = false

    fun onStart() {
        appRepository.start()
        shortcutRepository.start()
        if (!enumerated) {
            enumerated = true
            // Enumerating every installed app and shortcut is far too slow to run
            // before the first frame. The home screen renders immediately and the
            // lists fill in when this finishes; package changes after that arrive
            // through the registered callbacks.
            enumerate()
        }
        refreshHomeState()
    }

    fun onStop() {
        appRepository.stop()
        shortcutRepository.stop()
    }

    fun refreshHomeState() {
        _isDefaultHome.value = shortcutRepository.isDefaultHome()
        // Re-check on every resume/refresh: a grant or revocation made in Settings
        // while the launcher was away must update the UI without requesting anything.
        // This reads the current state only; it never launches a permission dialog.
        _calendarPermission.value = calendarRepository.hasPermission()
    }

    /**
     * Rescans installed apps and shortcuts off the main thread. The loading flag is
     * cleared in a `finally`, so a platform query that throws cannot leave the drawer
     * claiming to load forever: a stuck spinner on a home screen is worse than none.
     */
    private fun enumerate() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                appRepository.refresh()
                shortcutRepository.refresh()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshApps() {
        enumerate()
        refreshHomeState()
    }

    fun openApps() { _screen.value = Screen.APPS }

    fun openSettings(from: Screen = if (_screen.value == Screen.APPS) Screen.APPS else Screen.HOME) {
        // WALLPAPERS and CALENDAR are leaves of HOME; returning goes to HOME.
        settingsReturn = when (from) {
            Screen.APPS -> Screen.APPS
            else -> Screen.HOME
        }
        _screen.value = Screen.SETTINGS
    }

    /** Back from Settings returns to the drawer when entered from it, keeping the query. */
    fun backFromSettings() {
        _screen.value = settingsReturn
        settingsReturn = Screen.HOME
    }

    fun openWallpapers() { _screen.value = Screen.WALLPAPERS }

    fun openCalendar() {
        _screen.value = Screen.CALENDAR
        refreshCalendar()
    }

    fun goHome() {
        _screen.value = Screen.HOME
        settingsReturn = Screen.HOME
        _query.value = ""
    }

    // ---- Home: paging ---------------------------------------------------------------

    /**
     * The pager has settled on [index]. Records the page the user swiped to, and clears the
     * one-shot destination.
     *
     * Called from the *settled* page rather than from every scroll offset: the current page
     * changes at the halfway mark of a fling, and storing it there would persist a page the
     * user then abandoned.
     */
    fun onPagerSettled(index: Int) {
        settingsStore.settings.value.home.pages.getOrNull(index)?.let { settingsStore.setActivePage(it.id) }
        _pagerTargetPage.value = null
    }

    /**
     * Moves the pager to a page by index, for the routes that are not a swipe: the page
     * indicator, and "add a page, then go to it". The stored active id is written by the
     * settled callback, so this only has to move the pager.
     */
    fun goToPage(index: Int) {
        val layout = settingsStore.settings.value.home
        if (index in layout.pages.indices) _pagerTargetPage.value = index
    }

    // ---- Home: edit mode ------------------------------------------------------------

    /** Enters page editing. Reached from the bottom bar and from Settings. */
    fun enterPageEdit() { _pageEditing.value = true }

    /** Leaves page editing. Also the first thing Back does while editing. */
    fun leavePageEdit() { _pageEditing.value = false }

    /**
     * Back's whole decision for the launcher, in the order the user experiences it:
     * the pause dialog, then confirms, then whichever screen is on top, then the
     * list dialogs, then edit mode.
     *
     * Returning false means "there is nothing of mine to close", and the activity then does
     * what it did before this feature existed. A launcher must not treat Back on plain Home
     * as a way out of itself, and it must not slide the pager: neither is a layer, and both
     * would be a surprise.
     */
    fun back(): Boolean {
        if (_pendingPause.value != null) {
            cancelPause()
            return true
        }
        if (_showDiscardHighlight.value) {
            _showDiscardHighlight.value = false
            return true
        }
        if (_showDiscardTask.value) {
            _showDiscardTask.value = false
            return true
        }
        if (_confirmDeleteHighlight.value != null) {
            _confirmDeleteHighlight.value = null
            return true
        }
        if (_confirmDeleteTask.value != null) {
            _confirmDeleteTask.value = null
            return true
        }
        when (_screen.value) {
            Screen.ONBOARDING -> {
                dismissOnboarding()
                return true
            }
            Screen.SETTINGS -> {
                backFromSettings()
                return true
            }
            Screen.APPS -> {
                goHome()
                return true
            }
            Screen.WALLPAPERS, Screen.CALENDAR -> {
                goHome()
                return true
            }
            Screen.HIGHLIGHT_EDITOR -> {
                backFromHighlightEditor()
                return true
            }
            Screen.TASK_EDITOR -> {
                backFromTaskEditor()
                return true
            }
            Screen.HOME -> Unit
        }
        // List dialogs live on Home: they are layers above the page.
        if (_showAllHighlights.value) {
            _showAllHighlights.value = false
            return true
        }
        if (_showAllTasks.value) {
            _showAllTasks.value = false
            _editingTaskId.value = null
            return true
        }
        if (_pageEditing.value) {
            leavePageEdit()
            return true
        }
        return false
    }

    // ---- Home: pages ----------------------------------------------------------------

    /** Adds a page after the active one and moves to it. Null at the page limit. */
    fun addPage(): String? {
        val id = settingsStore.addPage() ?: return null
        _pagerTargetPage.value = settingsStore.settings.value.home.pages.indexOfFirst { it.id == id }
        return id
    }

    fun removePage(id: String) = settingsStore.removePage(id)

    fun renamePage(id: String, name: String) = settingsStore.renamePage(id, name)

    /** Moves a page one slot. The store clamps, so the ends are simply no-ops. */
    fun movePage(id: String, delta: Int) {
        val index = settingsStore.settings.value.home.pages.indexOfFirst { it.id == id }
        if (index >= 0) settingsStore.movePage(id, index + delta)
    }

    /**
     * Moves the pinned app named by [key] one slot inside [pageId].
     *
     * By key rather than by index because the drag that calls this recomposes the list
     * between slots: the index the row had when the gesture started is stale by the time
     * the next slot is crossed, and the key is not.
     */
    fun moveItemBy(pageId: String, key: String, delta: Int) {
        val keys = settingsStore.settings.value.home.page(pageId)?.config?.itemKeys ?: return
        val from = keys.indexOf(key)
        if (from >= 0) settingsStore.movePageItem(pageId, from, from + delta)
    }

    fun movePageItem(pageId: String, from: Int, to: Int) = settingsStore.movePageItem(pageId, from, to)

    fun moveFavourite(key: String, delta: Int) = settingsStore.moveFavourite(key, delta)

    // ---- Focus page ---------------------------------------------------------------

    /** Stores the Focus page's note. Written on every keystroke; there is no save step. */
    fun setFocusNote(note: String) { focusNoteStore.write(note) }

    /** Leaves onboarding for good: the seen flag is what keeps goHome() from returning here. */
    fun dismissOnboarding() {
        settingsStore.update { it.copy(onboardingSeen = true) }
        _screen.value = Screen.HOME
    }

    /** Re-opens onboarding from Settings without clearing the seen flag. */
    fun showOnboarding() { _screen.value = Screen.ONBOARDING }

    fun setQuery(query: String) { _query.value = query }

    /**
     * Empties the search without leaving the drawer. `goHome()` was previously the only
     * way to reset the query, so clearing it also meant abandoning the screen.
     */
    fun clearQuery() { _query.value = "" }

    /**
     * Launcher-level pre-launch only: a distraction-listed target with the pause on
     * opens the calm dialog instead of launching directly. Everything else launches.
     *
     * The optional access window narrows that further: while it is on, a listed app
     * passes straight through during the window and only pauses outside it. Read from
     * the device clock at the moment of the tap — there is no timer, no polling and no
     * scheduled wake-up, so the gate costs nothing while the launcher is idle and the
     * manifest stays free of ALARM/background-service entries.
     */
    fun launch(target: LaunchTarget) {
        val settings = settingsStore.settings.value
        val pause = AccessWindow.shouldPause(
            pauseEnabled = settings.pauseEnabled,
            inDistract = target.key in settings.distract,
            windowEnabled = settings.windowEnabled,
            nowMinute = nowMinuteOfDay(),
            startMinute = settings.windowStartMinute,
            endMinute = settings.windowEndMinute,
        )
        if (pause) {
            _pendingPause.value = target
        } else {
            appLauncher.launch(target)
        }
    }

    /** The dialog's Continue: one direct launch, no journaling. */
    fun confirmPause() {
        _pendingPause.value?.let { appLauncher.launch(it) }
        _pendingPause.value = null
    }

    /** The dialog's Cancel: back to the list, nothing launched. */
    fun cancelPause() { _pendingPause.value = null }

    fun toggleFavourite(key: String) = settingsStore.toggleFavourite(key)

    fun toggleDistract(key: String) = settingsStore.update { current ->
        val distract = if (key in current.distract) current.distract - key else current.distract + key
        current.copy(distract = distract)
    }

    /**
     * Hides or unhides an app, and keeps the undo offer in step with which way it went.
     * Hiding leaves a note naming the app for a few seconds; unhiding clears the note,
     * because the user has already undone the thing it was offering to undo.
     */
    fun toggleHidden(key: String) {
        val hiding = key !in settingsStore.settings.value.hidden
        settingsStore.toggleHidden(key)
        // One timer at a time: a second hide replaces the note rather than racing it.
        undoJob?.cancel()
        if (hiding) {
            _undoHidden.value = targetFor(key)
            undoJob = viewModelScope.launch {
                delay(6_000)
                _undoHidden.value = null
            }
        } else {
            _undoHidden.value = null
        }
    }

    /** Puts the app named by the undo note back, and takes the note down with it. */
    fun undoHide() {
        val target = _undoHidden.value ?: return
        settingsStore.toggleHidden(target.key)
        undoJob?.cancel()
        _undoHidden.value = null
    }

    /**
     * The target a hidden key belongs to. Discovery carries hidden apps in [UiState.allApps],
     * but a key can outlive the app it names, and the note still has to say something, so
     * the package name stands in as the label rather than leaving the line nameless.
     */
    private fun targetFor(key: String): LaunchTarget =
        state.value.allApps.firstOrNull { it.key == key }
            ?: LaunchTarget(
                packageName = key.substringBefore('/'),
                activityName = key.substringAfter('/', "").ifEmpty { null },
                label = key.substringBefore('/'),
            )

    fun setTheme(theme: ThemeMode) = settingsStore.update { it.copy(theme = theme) }

    fun setLayout(layout: LayoutMode) = settingsStore.update { it.copy(layout = layout) }

    fun setTodayEnabled(enabled: Boolean) = settingsStore.update { it.copy(todayEnabled = enabled) }

    fun setClock24h(enabled: Boolean) = settingsStore.update { it.copy(clock24h = enabled) }

    fun setZendoEnabled(enabled: Boolean) = settingsStore.update { it.copy(zendoEnabled = enabled) }

    fun setPauseEnabled(enabled: Boolean) = settingsStore.update { it.copy(pauseEnabled = enabled) }

    /**
     * The access-window toggle. Its values live on even while it is off, so turning it
     * back on restores the last window rather than resetting it.
     */
    fun setWindowEnabled(enabled: Boolean) = settingsStore.update { it.copy(windowEnabled = enabled) }

    fun setWindowStart(minuteOfDay: Int) =
        settingsStore.update { it.copy(windowStartMinute = minuteOfDay.coerceIn(0, AccessWindow.MINUTES_PER_DAY - 1)) }

    fun setWindowEnd(minuteOfDay: Int) =
        settingsStore.update { it.copy(windowEndMinute = minuteOfDay.coerceIn(0, AccessWindow.MINUTES_PER_DAY - 1)) }

    /**
     * The device's local wall-clock minute-of-day. Read at the moment of a tap and
     * nowhere else — no timer and no polling, so an idle launcher never wakes for this.
     *
     * A settable property rather than a direct [LocalTime.now] call so the access-window
     * tests can place the launcher at a fixed minute; nothing in the app assigns it.
     */
    internal var nowMinuteOfDay: () -> Int = {
        val now = LocalTime.now()
        now.hour * 60 + now.minute
    }

    // ---- Highlights (persistent, never auto-erased) ---------------------------------

    /**
     * Opens the full-screen highlight editor. Drafts are seeded here so rotation
     * and transient interruptions keep the user's text; the store is written only
     * on Save, never per keystroke (one keystroke must never create a record).
     */
    fun openHighlightEditor(id: String? = null) {
        val existing = highlightStore.highlights.value.firstOrNull { it.id == id }
        _editingHighlightId.value = existing?.id
        _highlightTitle.value = existing?.title.orEmpty()
        _highlightNote.value = existing?.note.orEmpty()
        _highlightPin.value = existing?.isPrimary ?: highlightStore.highlights.value.none { !it.archived }
        _highlightError.value = null
        _showDiscardHighlight.value = false
        _showAllHighlights.value = false
        _screen.value = Screen.HIGHLIGHT_EDITOR
    }

    fun updateHighlightDraft(title: String, note: String, pin: Boolean) {
        _highlightTitle.value = title
        _highlightNote.value = note
        _highlightPin.value = pin
        if (_highlightError.value != null && (title.isNotBlank() || note.isNotBlank())) {
            _highlightError.value = null
        }
    }

    /** True when the draft differs from the saved record (or is non-empty when new). */
    fun isHighlightDirty(): Boolean {
        val existing = highlightStore.highlights.value.firstOrNull { it.id == _editingHighlightId.value }
        return if (existing == null) {
            _highlightTitle.value.isNotBlank() || _highlightNote.value.isNotBlank()
        } else {
            _highlightTitle.value != existing.title ||
                _highlightNote.value != existing.note ||
                (_highlightPin.value && !existing.isPrimary)
        }
    }

    /**
     * Saves the draft. Title is optional: when blank but the body is not, the
     * first line of the body becomes the title (like a paper note). A fully
     * empty draft refuses with an inline error and the editor stays open, so no
     * input is ever lost to a silent no-op.
     */
    fun saveHighlight(): Boolean {
        val title = _highlightTitle.value.trim()
        val note = _highlightNote.value.trim()
        if (title.isEmpty() && note.isEmpty()) {
            _highlightError.value = "Add a title or some thoughts first."
            return false
        }
        val effectiveTitle = title.ifEmpty { note.lineSequence().first().take(80) }
        val id = _editingHighlightId.value
        val saved = highlightStore.upsert(effectiveTitle, note, id, _highlightPin.value)
        if (saved == null) {
            _highlightError.value = "Could not save. Try again."
            return false
        }
        clearHighlightDraft()
        _screen.value = Screen.HOME
        return true
    }

    /** Back from the editor: unsaved work asks, clean state returns Home. */
    fun backFromHighlightEditor() {
        if (isHighlightDirty()) {
            _showDiscardHighlight.value = true
        } else {
            clearHighlightDraft()
            _screen.value = Screen.HOME
        }
    }

    fun cancelDiscardHighlight() { _showDiscardHighlight.value = false }

    fun discardHighlight() {
        _showDiscardHighlight.value = false
        clearHighlightDraft()
        _screen.value = Screen.HOME
    }

    private fun clearHighlightDraft() {
        _editingHighlightId.value = null
        _highlightTitle.value = ""
        _highlightNote.value = ""
        _highlightPin.value = false
        _highlightError.value = null
        _showDiscardHighlight.value = false
    }

    fun pinHighlight(id: String) = highlightStore.pinAsPrimary(id)

    fun archiveHighlight(id: String, archived: Boolean = true) = highlightStore.archive(id, archived)

    fun requestDeleteHighlight(id: String) { _confirmDeleteHighlight.value = id }

    fun cancelDeleteHighlight() { _confirmDeleteHighlight.value = null }

    fun confirmDeleteHighlight() {
        _confirmDeleteHighlight.value?.let { highlightStore.delete(it) }
        _confirmDeleteHighlight.value = null
    }

    fun openAllHighlights() { _showAllHighlights.value = true }

    fun closeAllHighlights() { _showAllHighlights.value = false }

    // ---- Tasks ----------------------------------------------------------------------

    /** Quick Add: one line, immediate, clears. Blank input is ignored, never stored. */
    fun addTask(title: String) = taskStore.add(title)

    fun toggleTask(id: String) = taskStore.toggleDone(id)

    fun renameTask(id: String, title: String) = taskStore.rename(id, title)

    fun setTaskVisible(id: String, show: Boolean) = taskStore.setShowOnHome(id, show)

    fun requestDeleteTask(id: String) { _confirmDeleteTask.value = id }

    fun cancelDeleteTask() { _confirmDeleteTask.value = null }

    fun confirmDeleteTask() {
        _confirmDeleteTask.value?.let { taskStore.delete(it) }
        _confirmDeleteTask.value = null
    }

    fun openAllTasks() { _showAllTasks.value = true }

    fun closeAllTasks() {
        _showAllTasks.value = false
        _editingTaskId.value = null
    }

    /**
     * Opens the full-screen task editor (title + optional due date + notes).
     * Only title is required; due date and notes stay optional and are never
     * invented when the user leaves them empty.
     */
    fun openTaskEditor(id: String?) {
        val existing = taskStore.tasks.value.firstOrNull { it.id == id }
        _editingTaskId.value = existing?.id
        _taskTitle.value = existing?.title.orEmpty()
        _taskDue.value = existing?.dueDateMillis
        _taskNotes.value = existing?.notes.orEmpty()
        _taskError.value = null
        _showDiscardTask.value = false
        _showAllTasks.value = false
        _screen.value = Screen.TASK_EDITOR
    }

    fun updateTaskDraft(title: String, dueMillis: Long?, notes: String) {
        _taskTitle.value = title
        _taskDue.value = dueMillis
        _taskNotes.value = notes
        if (_taskError.value != null && title.isNotBlank()) _taskError.value = null
    }

    fun isTaskDirty(): Boolean {
        val existing = taskStore.tasks.value.firstOrNull { it.id == _editingTaskId.value }
        return if (existing == null) {
            _taskTitle.value.isNotBlank() || _taskNotes.value.isNotBlank() || _taskDue.value != null
        } else {
            _taskTitle.value != existing.title ||
                _taskDue.value != existing.dueDateMillis ||
                _taskNotes.value != existing.notes
        }
    }

    /** Saves the task editor. Blank title refuses with an inline error; nothing is lost. */
    fun saveTask(): Boolean {
        val saved = taskStore.updateDetails(
            _editingTaskId.value,
            _taskTitle.value,
            _taskDue.value,
            _taskNotes.value,
        )
        if (saved == null) {
            _taskError.value = "Give the task a title first."
            return false
        }
        clearTaskDraft()
        _screen.value = Screen.HOME
        return true
    }

    fun backFromTaskEditor() {
        if (isTaskDirty()) {
            _showDiscardTask.value = true
        } else {
            clearTaskDraft()
            _screen.value = Screen.HOME
        }
    }

    fun cancelDiscardTask() { _showDiscardTask.value = false }

    fun discardTask() {
        _showDiscardTask.value = false
        clearTaskDraft()
        _screen.value = Screen.HOME
    }

    private fun clearTaskDraft() {
        _editingTaskId.value = null
        _taskTitle.value = ""
        _taskDue.value = null
        _taskNotes.value = ""
        _taskError.value = null
        _showDiscardTask.value = false
    }

    // ---- Calendar (read-only device provider) ---------------------------------------

    fun selectedDate(): LocalDate = _selectedDate.value

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        refreshCalendarEvents()
    }

    fun refreshCalendarPermission() {
        _calendarPermission.value = calendarRepository.hasPermission()
    }

    /**
     * Records the user's answer to the calendar permission dialog. A denial marks
     * the ask as done so the UI can route a repeat tap to the app Settings page
     * instead of re-firing a system dialog the platform would auto-deny.
     */
    fun onCalendarPermissionResult(granted: Boolean) {
        if (!granted) setCalendarAsked(true)
        refreshCalendarPermission()
    }

    fun setCalendarAsked(asked: Boolean) = settingsStore.update { it.copy(calendarAsked = asked) }

    fun refreshCalendar() {
        refreshCalendarPermission()
        viewModelScope.launch(Dispatchers.IO) {
            val cals = try {
                calendarRepository.listCalendars()
            } catch (_: Exception) {
                emptyList()
            }
            _calendars.value = cals
            refreshCalendarEvents()
        }
    }

    fun refreshCalendarEvents() {
        if (!calendarRepository.hasPermission()) {
            _calendarEvents.value = emptyList()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val date = _selectedDate.value
            val zone = ZoneId.systemDefault()
            val weekStart = date.minusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
            val weekEnd = date.plusDays(14).atStartOfDay(zone).toInstant().toEpochMilli()
            val shown = settingsStore.settings.value.shownCalendarIds
            val filter = shown.ifEmpty { null }
            _calendarEvents.value = try {
                calendarRepository.queryEvents(weekStart, weekEnd, filter)
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    fun setShownCalendars(ids: Set<Long>) {
        settingsStore.update { it.copy(shownCalendarIds = ids) }
        refreshCalendarEvents()
    }

    fun eventsFor(date: LocalDate): List<CalendarEvent> {
        val zone = ZoneId.systemDefault()
        return _calendarEvents.value.filter {
            val d = java.time.Instant.ofEpochMilli(it.startMillis).atZone(zone).toLocalDate()
            d == date || (it.allDay && d == date)
        }.sortedBy { it.startMillis }
    }

    fun upcomingFromToday(limit: Int = 5): List<CalendarEvent> {
        if (!calendarRepository.hasPermission()) return emptyList()
        val now = System.currentTimeMillis()
        return _calendarEvents.value.filter { it.endMillis >= now }.sortedBy { it.startMillis }.take(limit)
    }

    // ---- Wallpaper ------------------------------------------------------------------

    fun selectWallpaper(id: String) {
        wallpaperStore.select(id)
        _wallpaperMessage.value = null
    }

    fun setWallpaperTarget(target: WallpaperTarget) = wallpaperStore.setTarget(target)

    fun clearWallpaperMessage() { _wallpaperMessage.value = null }

    fun applyWallpaper() {
        // A second tap while the first is still running must not start a duplicate
        // operation: the UI already swaps the button for status text, and this is the
        // second line of defence for the frame in between.
        if (_wallpaperApplying.value) return
        val template = WallpaperTemplates.byId(wallpaperStore.selectedId.value) ?: run {
            _wallpaperMessage.value = "Choose a wallpaper first."
            return
        }
        val target = wallpaperStore.target.value
        _wallpaperApplying.value = true
        _wallpaperMessage.value = null
        viewModelScope.launch(Dispatchers.IO) {
            val result = WallpaperApplier.apply(getApplication(), template, target)
            when (result) {
                is WallpaperApplyResult.Applied -> {
                    wallpaperStore.recordResult(true)
                    _wallpaperMessage.value = "Applied to ${target.name.lowercase()}."
                }
                is WallpaperApplyResult.Partial -> {
                    wallpaperStore.recordResult(true)
                    _wallpaperMessage.value = result.message
                }
                is WallpaperApplyResult.Failed -> {
                    wallpaperStore.recordResult(false)
                    _wallpaperMessage.value = result.message
                }
            }
            _wallpaperApplying.value = false
        }
    }

    // ---- Gestures -------------------------------------------------------------------

    fun setSwipeUpEnabled(enabled: Boolean) = settingsStore.update { it.copy(swipeUpEnabled = enabled) }

    fun setPagingEnabled(enabled: Boolean) = settingsStore.update { it.copy(pagingEnabled = enabled) }

    fun setReduceMotion(enabled: Boolean) = settingsStore.update { it.copy(reduceMotion = enabled) }

    suspend fun bitmapFor(target: LaunchTarget, sizePx: Int): Bitmap? =
        withContext(Dispatchers.IO) { appRepository.bitmapFor(target, sizePx) }
}
