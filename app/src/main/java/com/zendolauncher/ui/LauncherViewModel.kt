package com.zendolauncher.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zendolauncher.BuildConfig
import com.zendolauncher.core.AppQuery
import com.zendolauncher.core.BridgeParse
import com.zendolauncher.core.BridgePlan
import com.zendolauncher.core.BridgePlanner
import com.zendolauncher.core.BridgeSnapshot
import com.zendolauncher.core.DayRecord
import com.zendolauncher.core.Highlight
import com.zendolauncher.core.ImportMode
import com.zendolauncher.core.Origins
import com.zendolauncher.core.TaskItem
import com.zendolauncher.core.WallpaperTemplates
import com.zendolauncher.core.primary
import com.zendolauncher.data.BackupContents
import com.zendolauncher.data.BackupParse
import com.zendolauncher.data.BackupPrefs
import com.zendolauncher.data.BridgeJson
import com.zendolauncher.data.FocusNoteStore
import com.zendolauncher.data.HighlightStore
import com.zendolauncher.data.HistoryStore
import com.zendolauncher.data.LayoutMode
import com.zendolauncher.data.LauncherSettings
import com.zendolauncher.data.SettingsStore
import com.zendolauncher.data.TaskStore
import com.zendolauncher.data.ThemeMode
import com.zendolauncher.data.TodayActionStore
import com.zendolauncher.data.WallpaperStore
import com.zendolauncher.data.WallpaperTarget
import com.zendolauncher.model.AppUsage
import com.zendolauncher.model.CalendarEvent
import com.zendolauncher.model.DeviceCalendar
import com.zendolauncher.model.LaunchTarget
import com.zendolauncher.platform.AppLauncher
import com.zendolauncher.platform.AppRepository
import com.zendolauncher.platform.CalendarRepository
import com.zendolauncher.platform.ShortcutRepository
import com.zendolauncher.platform.UsageRepository
import com.zendolauncher.platform.WallpaperApplier
import com.zendolauncher.platform.WallpaperApplyResult
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

enum class Screen { ONBOARDING, HOME, APPS, SETTINGS, WALLPAPERS, CALENDAR, HISTORY, HIGHLIGHT_EDITOR, TASK_EDITOR, SETUP, PERMISSIONS }

/**
 * The planned result of a picked snapshot file, held while the user reviews and
 * confirms it. The plan is computed once per mode; applying it writes exactly the
 * lists the preview counted.
 */
data class BridgePreviewUi(
    val fileName: String,
    val snapshot: BridgeSnapshot,
    val plan: BridgePlan,
) {
    val mode: ImportMode get() = plan.mode
}

/** A parsed backup file waiting on the restore confirmation. */
data class BackupPreviewUi(
    val fileName: String,
    val contents: BackupContents,
)

data class UiState(
    val screen: Screen = Screen.HOME,
    val query: String = "",
    val settings: LauncherSettings = LauncherSettings(),
    val favourites: List<LaunchTarget> = emptyList(),
    val results: List<LaunchTarget> = emptyList(),
    val visibleApps: List<LaunchTarget> = emptyList(),
    val shortcutAccess: Boolean = false,
    val isDefaultHome: Boolean = false,
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
    /** The note shown when a pin was refused because the page is full; see [LauncherViewModel.toggleFavourite]. */
    val pinNotice: String? = null,
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
    /** The day's headline action. Empty means none set yet. */
    val todayActionTitle: String = "",
    val todayActionDone: Boolean = false,
    val editingTodayAction: Boolean = false,
    val todayActionDraft: String = "",
    val todayActionError: String? = null,
    // ---- History (local day records) --------------------------------------------
    val historyDays: List<DayRecord> = emptyList(),
    val historyLoading: Boolean = false,
    val historyMessage: String? = null,
    val confirmClearHistory: Boolean = false,
    // ---- Zendo bridge & backup (manual files only) --------------------------------
    val bridgePreview: BridgePreviewUi? = null,
    val backupPreview: BackupPreviewUi? = null,
    val zendoMessage: String? = null,
    // ---- Calendar (device provider, read-only) ---------------------------------
    val calendars: List<DeviceCalendar> = emptyList(),
    val calendarEvents: List<CalendarEvent> = emptyList(),
    val calendarPermission: Boolean = false,
    val calendarLoading: Boolean = false,
    /** Last error message, or null. Kept events stay visible beneath it (marked stale). */
    val calendarError: String? = null,
    /** Last successful events refresh, wall-clock millis. 0 = never. */
    val lastCalendarRefresh: Long = 0L,
    val selectedDateEpochDay: Long = LocalDate.now().toEpochDay(),
    // ---- Wallpaper ------------------------------------------------------------
    val wallpaperSelectedId: String? = null,
    val wallpaperTarget: WallpaperTarget = WallpaperTarget.BOTH,
    val wallpaperApplying: Boolean = false,
    val wallpaperMessage: String? = null,
    // ---- Optional usage summary (special access, numbers only) -------------------
    val usageAccess: Boolean = false,
    val usageEntries: List<AppUsage> = emptyList(),
    val usageLoading: Boolean = false,
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
    private val todayActionStore = TodayActionStore(application)
    private val historyStore = HistoryStore(application)
    private val wallpaperStore = WallpaperStore(application)
    private val calendarRepository = CalendarRepository(application)
    private val usageRepository = UsageRepository(application)
    private val appLauncher = AppLauncher(application)

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

    /** The note for a refused pin; same auto-dismiss pattern as the undo note. */
    private val _pinNotice = MutableStateFlow<String?>(null)

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
    private val _calendarLoading = MutableStateFlow(false)
    private val _calendarError = MutableStateFlow<String?>(null)
    private val _lastCalendarRefresh = MutableStateFlow(0L)
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
    // Today action inline-edit draft (one line; submitted or discarded at once).
    private val _editingTodayAction = MutableStateFlow(false)
    private val _todayActionDraft = MutableStateFlow("")
    private val _todayActionError = MutableStateFlow<String?>(null)
    // Usage summary: explicit loads only, never background polling. Gated on
    // screenTimeEnabled; when off, no query ever runs and the lists stay empty.
    private val _usageAccess = MutableStateFlow(false)
    private val _usageEntries = MutableStateFlow<List<AppUsage>>(emptyList())
    private val _usageLoading = MutableStateFlow(false)

    // History / bridge / backup state. Local files only; nothing here touches a network.
    private val _historyLoading = MutableStateFlow(false)
    private val _historyMessage = MutableStateFlow<String?>(null)
    private val _confirmClearHistory = MutableStateFlow(false)
    private val _bridgePreview = MutableStateFlow<BridgePreviewUi?>(null)
    private val _backupPreview = MutableStateFlow<BackupPreviewUi?>(null)
    private val _zendoMessage = MutableStateFlow<String?>(null)

    /** Where Back returns from Settings; lets Back-to-drawer keep the drawer query. */
    private var settingsReturn: Screen = Screen.HOME

    /** Where Back returns from Setup / Permissions (usually HOME or SETTINGS). */
    private var setupReturn: Screen = Screen.HOME

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
        combine(lists, prefs, _isLoading, _undoHidden, _pinNotice) { lists, prefs, isLoading, undoHidden, pinNotice ->
            UiState(
                screen = lists.screen,
                query = prefs.query,
                settings = prefs.settings,
                favourites = lists.apps.favourites,
                results = lists.apps.results,
                visibleApps = lists.apps.visibleApps,
                shortcutAccess = prefs.shortcutAccess,
                isDefaultHome = prefs.isDefaultHome,
                allApps = lists.apps.allApps,
                undoHidden = undoHidden,
                pinNotice = pinNotice,
                isLoading = isLoading,
                pageEditing = lists.pageEditing,
                pagerTargetPage = lists.pagerTargetPage,
                focusNote = prefs.focusNote,
            )
        }

    private data class TodayBundle(
        val highlights: List<Highlight>,
        val tasks: List<TaskItem>,
        val actionTitle: String,
        val actionDone: Boolean,
        val editingAction: Boolean,
        val actionDraft: String,
        val actionError: String?,
        val calendars: List<DeviceCalendar>,
        val events: List<CalendarEvent>,
        val permission: Boolean,
        val loading: Boolean,
        val calError: String?,
        val lastRefresh: Long,
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
        val usageAccess: Boolean,
        val usageEntries: List<AppUsage>,
        val usageLoading: Boolean,
    )

    private val today: Flow<TodayBundle> = combine(
        highlightStore.highlights,
        taskStore.tasks,
        todayActionStore.title,
        todayActionStore.done,
        _editingTodayAction,
        _todayActionDraft,
        _todayActionError,
        _calendars,
        _calendarEvents,
        _calendarPermission,
        _calendarLoading,
        _calendarError,
        _lastCalendarRefresh,
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
        _usageAccess,
        _usageEntries,
        _usageLoading,
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        TodayBundle(
            highlights = args[0] as List<Highlight>,
            tasks = args[1] as List<TaskItem>,
            actionTitle = args[2] as String,
            actionDone = args[3] as Boolean,
            editingAction = args[4] as Boolean,
            actionDraft = args[5] as String,
            actionError = args[6] as String?,
            calendars = args[7] as List<DeviceCalendar>,
            events = args[8] as List<CalendarEvent>,
            permission = args[9] as Boolean,
            loading = args[10] as Boolean,
            calError = args[11] as String?,
            lastRefresh = args[12] as Long,
            date = args[13] as LocalDate,
            wallpaperId = args[14] as String?,
            wallpaperTarget = args[15] as WallpaperTarget,
            applying = args[16] as Boolean,
            message = args[17] as String?,
            editingId = args[18] as String?,
            draftTitle = args[19] as String,
            draftNote = args[20] as String,
            draftPin = args[21] as Boolean,
            draftError = args[22] as String?,
            showDiscardH = args[23] as Boolean,
            confirmDelH = args[24] as String?,
            showAllH = args[25] as Boolean,
            showAllT = args[26] as Boolean,
            editingTask = args[27] as String?,
            taskTitle = args[28] as String,
            taskDue = args[29] as Long?,
            taskNotes = args[30] as String,
            taskError = args[31] as String?,
            showDiscardT = args[32] as Boolean,
            confirmDelT = args[33] as String?,
            usageAccess = args[34] as Boolean,
            usageEntries = args[35] as List<AppUsage>,
            usageLoading = args[36] as Boolean,
        )
    }

    /**
     * History and file-transfer state, kept apart from [TodayBundle] so neither
     * `combine` outgrows its arity. All local; none of it is network state.
     */
    private data class Extras(
        val historyDays: List<DayRecord>,
        val historyLoading: Boolean,
        val historyMessage: String?,
        val confirmClearHistory: Boolean,
        val bridgePreview: BridgePreviewUi?,
        val backupPreview: BackupPreviewUi?,
        val zendoMessage: String?,
    )

    private val extras: Flow<Extras> = combine(
        historyStore.days,
        _historyLoading,
        _historyMessage,
        _confirmClearHistory,
        _bridgePreview,
        _backupPreview,
        _zendoMessage,
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        Extras(
            historyDays = args[0] as List<DayRecord>,
            historyLoading = args[1] as Boolean,
            historyMessage = args[2] as String?,
            confirmClearHistory = args[3] as Boolean,
            bridgePreview = args[4] as BridgePreviewUi?,
            backupPreview = args[5] as BackupPreviewUi?,
            zendoMessage = args[6] as String?,
        )
    }

    val state: StateFlow<UiState> =
        combine(base, _pendingPause, today, extras) { base, pendingPause, t, x ->
            base.copy(
                pendingPause = pendingPause,
                highlights = t.highlights,
                tasks = t.tasks,
                todayActionTitle = t.actionTitle,
                todayActionDone = t.actionDone,
                editingTodayAction = t.editingAction,
                todayActionDraft = t.actionDraft,
                todayActionError = t.actionError,
                calendars = t.calendars,
                calendarEvents = t.events,
                calendarPermission = t.permission,
                calendarLoading = t.loading,
                calendarError = t.calError,
                lastCalendarRefresh = t.lastRefresh,
                selectedDateEpochDay = t.date.toEpochDay(),
                // A stored id from an older build resolves to the default design
                // instead of reading as "nothing chosen".
                wallpaperSelectedId = t.wallpaperId
                    ?.takeIf { WallpaperTemplates.byId(it) != null }
                    ?: WallpaperTemplates.DEFAULT_ID,
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
                usageAccess = t.usageAccess,
                usageEntries = t.usageEntries,
                usageLoading = t.usageLoading,
                historyDays = x.historyDays,
                historyLoading = x.historyLoading,
                historyMessage = x.historyMessage,
                confirmClearHistory = x.confirmClearHistory,
                bridgePreview = x.bridgePreview,
                backupPreview = x.backupPreview,
                zendoMessage = x.zendoMessage,
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

    init {
        // Keep today's history record in step with the live stores. Every write goes
        // through upsert(), which ignores identical content, so this costs one
        // comparison per edit and nothing else. The date is read at capture time:
        // once the local date moves on, the previous day simply stops being rewritten
        // (past records are never touched), which is why no midnight job is needed.
        viewModelScope.launch {
            combine(
                highlightStore.highlights,
                taskStore.tasks,
                todayActionStore.title,
                todayActionStore.done,
            ) { highlights, tasks, title, done ->
                historyStore.capture(
                    date = LocalDate.now(),
                    actionTitle = title,
                    actionDone = done,
                    highlightText = highlights.primary()?.title.orEmpty(),
                    tasks = tasks,
                )
            }.collect { }
        }
    }

    private var enumerated = false

    fun onStart() {
        appRepository.start()
        shortcutRepository.start()
        registerCalendarObserver()
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
        unregisterCalendarObserver()
    }

    /**
     * Provider change notifications: the system tells us the calendar data may
     * have changed, and only then do we re-query — once, off the main thread.
     * No polling, no timers.
     */
    private var calendarObserver: android.database.ContentObserver? = null

    private fun registerCalendarObserver() {
        if (calendarObserver != null) return
        val observer = object : android.database.ContentObserver(
            android.os.Handler(android.os.Looper.getMainLooper()),
        ) {
            override fun onChange(selfChange: Boolean) {
                if (calendarRepository.hasPermission()) refreshCalendarEvents()
            }
        }
        calendarObserver = observer
        runCatching {
            getApplication<Application>().contentResolver.registerContentObserver(
                android.provider.CalendarContract.Instances.CONTENT_URI,
                true,
                observer,
            )
        }.onFailure { calendarObserver = null }
    }

    private fun unregisterCalendarObserver() {
        val observer = calendarObserver ?: return
        calendarObserver = null
        runCatching {
            getApplication<Application>().contentResolver.unregisterContentObserver(observer)
        }
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
            Screen.SETUP, Screen.PERMISSIONS -> from
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
        if (_confirmClearHistory.value) {
            cancelClearHistory()
            return true
        }
        if (_bridgePreview.value != null) {
            cancelBridgeImport()
            return true
        }
        if (_backupPreview.value != null) {
            cancelBackupRestore()
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
            Screen.WALLPAPERS, Screen.CALENDAR, Screen.HISTORY -> {
                goHome()
                return true
            }
            Screen.SETUP -> {
                backFromSetup()
                return true
            }
            Screen.PERMISSIONS -> {
                backFromPermissions()
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

    /** Leaves onboarding for the setup checklist. Seen is set when setup finishes. */
    fun dismissOnboarding() {
        _screen.value = Screen.SETUP
    }

    /** Finishes first-run setup for good. Every step is optional and repeatable later. */
    fun finishSetup() {
        settingsStore.update { it.copy(onboardingSeen = true) }
        _screen.value = Screen.HOME
    }

    fun openSetup(from: Screen = Screen.HOME) {
        setupReturn = from
        _screen.value = Screen.SETUP
    }

    fun backFromSetup() {
        // First-run Back is a Skip: the session continues on Home and nothing is lost,
        // and onboarding will not show again.
        if (!settingsStore.settings.value.onboardingSeen) {
            finishSetup()
            return
        }
        _screen.value = setupReturn
        setupReturn = Screen.HOME
    }

    fun openPermissions(from: Screen = Screen.HOME) {
        setupReturn = from
        _screen.value = Screen.PERMISSIONS
    }

    fun backFromPermissions() {
        _screen.value = setupReturn
        setupReturn = Screen.HOME
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

    /**
     * Pins or unpins an app. When the pin is refused — the target page already holds
     * the limit — a short note says so in place; the list is not reordered and
     * nothing is silently dropped.
     */
    fun toggleFavourite(key: String) {
        if (settingsStore.toggleFavourite(key)) {
            _pinNotice.value = null
        } else {
            _pinNotice.value = "This page is full. Unpin an app first."
            undoJob?.cancel()
            undoJob = viewModelScope.launch {
                delay(6_000)
                _pinNotice.value = null
            }
        }
    }

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

    /** Draws launcher-page icons desaturated; the original icon stays the fallback. */
    fun setMonochromeIcons(enabled: Boolean) = settingsStore.update { it.copy(monochromeIcons = enabled) }

    /** Pinned apps allowed on one page. 5 or 6; the store clamps anything else. */
    fun setPinLimit(limit: Int) = settingsStore.update {
        it.copy(pinLimit = limit.coerceIn(SettingsStore.MIN_PIN_LIMIT, SettingsStore.MAX_PIN_LIMIT))
    }

    /**
     * Screen time is opt-in: turning it on changes nothing until the system grants
     * Usage Access, and this flag is what every usage read is gated on. Turning it
     * off stops the queries and drops whatever was loaded.
     */
    fun setScreenTimeEnabled(enabled: Boolean) {
        settingsStore.update { it.copy(screenTimeEnabled = enabled) }
        if (!enabled) {
            _usageAccess.value = false
            _usageEntries.value = emptyList()
        }
    }

    /** Hides the numbers while access stays. Independent of [setScreenTimeEnabled]. */
    fun setScreenTimeHidden(hidden: Boolean) = settingsStore.update { it.copy(screenTimeHidden = hidden) }

    fun setPauseEnabled(enabled: Boolean) = settingsStore.update { it.copy(pauseEnabled = enabled) }

    /** The user-written reminder shown in the pause dialog. Blank resets to the default. */
    fun setPauseMessage(message: String) = settingsStore.update {
        it.copy(pauseMessage = message.trim().ifEmpty { com.zendolauncher.data.SettingsStore.DEFAULT_PAUSE_MESSAGE })
    }

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

    // ---- Today action (the day's headline, completable) ---------------------------

    fun editTodayAction() {
        _todayActionDraft.value = todayActionStore.title.value
        _todayActionError.value = null
        _editingTodayAction.value = true
    }

    fun updateTodayActionDraft(draft: String) {
        _todayActionDraft.value = draft
        if (_todayActionError.value != null && draft.isNotBlank()) _todayActionError.value = null
    }

    fun saveTodayAction(): Boolean {
        if (!todayActionStore.save(_todayActionDraft.value)) {
            _todayActionError.value = "Give today a headline first."
            return false
        }
        _editingTodayAction.value = false
        _todayActionError.value = null
        return true
    }

    fun cancelTodayActionEdit() {
        _editingTodayAction.value = false
        _todayActionError.value = null
    }

    fun toggleTodayAction() = todayActionStore.setDone(!todayActionStore.done.value)

    // ---- Focus timer ---------------------------------------------------------------
    // The timer belongs to the Zendo app; the launcher keeps only the per-page quick
    // note. The stored work/break minutes stay in settings as inert data so a
    // downgrade still finds them, and nothing in this app reads them any more.

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

    fun setCalendarGoogleOnly(googleOnly: Boolean) = settingsStore.update {
        it.copy(calendarGoogleOnly = googleOnly)
    }

    /**
     * The calendar ids the agenda may read: the user's explicit selection, always.
     * Empty means none chosen — nothing is ever auto-enabled. The legacy Google-only
     * filter still applies for installs that stored it before the selection became
     * explicit (their migration writes the narrowed view down and clears the flag).
     */
    fun effectiveCalendarIds(
        shown: Set<Long>,
        calendars: List<DeviceCalendar>,
        googleOnly: Boolean,
    ): Set<Long> {
        if (!googleOnly) return shown
        return shown.intersect(calendars.filter { it.isGoogle }.map { it.id }.toSet())
    }

    /**
     * Ends the legacy "empty means all" era the first time calendars are readable:
     * an upgrading install gets its previous view written down once, so nothing
     * silently disappears; a fresh install simply starts with none chosen.
     */
    private fun settleCalendarSelection(cals: List<DeviceCalendar>) {
        val settings = settingsStore.settings.value
        if (settings.calendarSelectionSchema || cals.isEmpty()) return
        if (settings.shownCalendarIds.isNotEmpty()) {
            settingsStore.materializeCalendarSelection(null)
            return
        }
        val ids = if (settings.calendarGoogleOnly) {
            cals.filter { it.isGoogle }.map { it.id }.toSet()
        } else {
            cals.map { it.id }.toSet()
        }
        settingsStore.materializeCalendarSelection(ids)
    }

    fun refreshCalendar() {
        refreshCalendarPermission()
        viewModelScope.launch(Dispatchers.IO) {
            val cals = try {
                calendarRepository.listCalendars()
            } catch (_: Exception) {
                emptyList()
            }
            _calendars.value = cals
            settleCalendarSelection(cals)
            refreshCalendarEvents()
        }
    }

    fun refreshCalendarEvents() {
        if (!calendarRepository.hasPermission()) {
            _calendarEvents.value = emptyList()
            _calendarLoading.value = false
            _calendarError.value = null
            return
        }
        _calendarLoading.value = true
        _calendarError.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val date = _selectedDate.value
                val zone = ZoneId.systemDefault()
                val weekStart = date.minusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
                val weekEnd = date.plusDays(14).atStartOfDay(zone).toInstant().toEpochMilli()
                val settings = settingsStore.settings.value
                val filter = effectiveCalendarIds(settings.shownCalendarIds, _calendars.value, settings.calendarGoogleOnly)
                _calendarEvents.value = if (filter.isEmpty()) {
                    emptyList()
                } else {
                    calendarRepository.queryEvents(weekStart, weekEnd, filter)
                }
                // Success replaces the previous answer; failure keeps it, marked stale.
                _calendarError.value = null
                _lastCalendarRefresh.value = System.currentTimeMillis()
            } catch (_: Exception) {
                _calendarError.value = "Couldn't read the calendar. Check sync, then retry."
            } finally {
                _calendarLoading.value = false
            }
        }
    }

    /**
     * Called when the Plan page settles into view. Refreshes only when
     * the cache is stale (older than a minute) — returning from an external
     * calendar minutes later re-queries once; swiping past does not hammer the
     * provider. Pure staleness rule in [calendarStale].
     */
    fun onPlanPageVisible(nowMillis: Long = System.currentTimeMillis()) {
        if (!calendarRepository.hasPermission()) return
        if (com.zendolauncher.core.UpcomingMerge.calendarStale(_lastCalendarRefresh.value, nowMillis)) {
            refreshCalendarEvents()
        }
    }

    fun clearCalendarError() { _calendarError.value = null }

    /** Writes the explicit selection and re-queries; empty is a valid, deliberate state. */
    fun setShownCalendars(ids: Set<Long>) {
        settingsStore.materializeCalendarSelection(ids)
        refreshCalendarEvents()
    }

    fun eventsFor(date: LocalDate): List<CalendarEvent> {
        val zone = ZoneId.systemDefault()
        return _calendarEvents.value.filter {
            com.zendolauncher.core.CalendarWeek.coversDate(it.startMillis, it.endMillis, date, zone)
        }.sortedBy { it.startMillis }
    }

    /** Deep-link for an event's details, opened in the user's own calendar app. */
    fun eventDetailsUri(eventId: Long): android.net.Uri =
        calendarRepository.eventUri(eventId)

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
        // An unknown stored id (a template from an older build) applies the default
        // design rather than stopping at a message about a choice the user cannot see.
        val template = WallpaperTemplates.byId(wallpaperStore.selectedId.value)
            ?: WallpaperTemplates.default()
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

    // ---- Optional screen time (special access, numbers only, opt-in) -----------------

    /**
     * Re-reads special-access state. Read-only; granting happens in system Settings.
     * Gated on [LauncherSettings.screenTimeEnabled]: while screen time is off, no
     * access is ever reported and nothing is loaded.
     */
    fun refreshUsageAccess() {
        val enabled = settingsStore.settings.value.screenTimeEnabled
        val has = enabled && usageRepository.hasAccess()
        _usageAccess.value = has
        if (!has) _usageEntries.value = emptyList()
    }

    /**
     * Loads today's summary on an explicit tap. No polling, no background work:
     * each load is one bounded provider query for the current day, and only while
     * screen time is on.
     */
    fun loadUsage() {
        if (!settingsStore.settings.value.screenTimeEnabled || !usageRepository.hasAccess()) {
            _usageAccess.value = false
            _usageEntries.value = emptyList()
            return
        }
        _usageAccess.value = true
        _usageLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            _usageEntries.value = try {
                usageRepository.todaySummary()
            } catch (_: Exception) {
                emptyList()
            }
            _usageLoading.value = false
        }
    }

    // ---- History (local day records) -------------------------------------------------

    fun openHistory() {
        _screen.value = Screen.HISTORY
        refreshHistory()
    }

    /** Re-reads the local record file off the main thread; the file is small. */
    fun refreshHistory() {
        _historyLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            historyStore.reload()
            _historyLoading.value = false
        }
    }

    fun clearHistoryMessage() { _historyMessage.value = null }

    fun requestClearHistory() { _confirmClearHistory.value = true }

    fun cancelClearHistory() { _confirmClearHistory.value = false }

    /**
     * Erases every day record. Live data (action, highlight, tasks) is untouched:
     * this is history, not the present. The next capture starts a fresh record for
     * today the moment anything changes.
     */
    fun confirmClearHistory() {
        historyStore.clear()
        _confirmClearHistory.value = false
        _historyMessage.value = "History cleared. Nothing else was touched."
    }

    // ---- Zendo bridge & backup (manual files only) -----------------------------------

    /**
     * Parses a picked snapshot file and holds the planned import for confirmation.
     * A file that is not a snapshot, is newer than this app, or is dated in the
     * future is refused here with a sentence, and nothing is written.
     */
    fun beginSnapshotImport(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val today = LocalDate.now()
            when (val parsed = BridgeJson.parseSnapshot(readTextFile(uri), today)) {
                is BridgeParse.Rejected -> _zendoMessage.value = parsed.message
                is BridgeParse.Ok -> {
                    _bridgePreview.value = BridgePreviewUi(
                        fileName = fileNameOf(uri),
                        snapshot = parsed.snapshot,
                        plan = planFor(parsed.snapshot, ImportMode.MERGE, today),
                    )
                    _zendoMessage.value = null
                }
            }
        }
    }

    /** Toggles merge/replace in the preview; the plan is recomputed from the same file. */
    fun setBridgeImportMode(mode: ImportMode) {
        val preview = _bridgePreview.value ?: return
        _bridgePreview.value = preview.copy(
            plan = planFor(preview.snapshot, mode, LocalDate.now()),
        )
    }

    private fun planFor(snapshot: BridgeSnapshot, mode: ImportMode, today: LocalDate): BridgePlan =
        BridgePlanner.plan(
            snapshot = snapshot,
            today = today,
            mode = mode,
            currentActionTitle = todayActionStore.title.value,
            currentActionOrigin = todayActionStore.origin.value,
            currentHighlights = highlightStore.highlights.value,
            currentTasks = taskStore.tasks.value,
            currentDay = historyStore.recordFor(snapshot.localDate),
        )

    fun cancelBridgeImport() { _bridgePreview.value = null }

    /**
     * Applies exactly the lists the preview counted. A snapshot for today lands in
     * the live stores (and is therefore captured into today's history as the day
     * goes on); a snapshot for a past date writes only that day's record.
     */
    fun confirmBridgeImport() {
        val preview = _bridgePreview.value ?: return
        val plan = preview.plan
        if (plan.isToday) {
            plan.actionTitle?.let { title ->
                todayActionStore.replace(
                    title = title,
                    done = false,
                    origin = Origins.ZENDO_PWA,
                    sourceId = preview.snapshot.action?.id,
                )
            }
            plan.highlights?.let { highlightStore.replaceAll(it) }
            plan.tasks?.let { taskStore.replaceAll(it) }
        }
        plan.dayRecord?.let { historyStore.upsert(it) }
        settingsStore.update {
            it.copy(
                zendoLastImportAt = System.currentTimeMillis(),
                zendoLastImportExportedAt = preview.snapshot.exportedAt,
                zendoLastImportDate = preview.snapshot.localDate.toString(),
            )
        }
        _bridgePreview.value = null
        _zendoMessage.value = importResultMessage(plan)
    }

    /** One plain sentence about what an import did — from the same plan that applied. */
    private fun importResultMessage(plan: BridgePlan): String {
        val s = plan.summary
        if (!s.hasAnyChange) return "Already up to date — nothing needed to change."
        val parts = ArrayList<String>()
        if (s.addedTasks > 0) parts += "${s.addedTasks} task${if (s.addedTasks == 1) "" else "s"} added"
        if (s.updatedTasks > 0) parts += "${s.updatedTasks} updated"
        if (s.removedImportedTasks > 0) parts += "${s.removedImportedTasks} replaced"
        if (s.actionSet) parts += "action set"
        if (s.highlightAdded) parts += "highlight added"
        if (s.highlightUpdated) parts += "highlight updated"
        return "Imported: " + parts.joinToString(", ") + "."
    }

    fun clearZendoMessage() { _zendoMessage.value = null }

    /** Parses a picked backup file; the restore itself waits for confirmation. */
    fun beginBackupImport(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            when (val parsed = BridgeJson.parseBackup(readTextFile(uri))) {
                is BackupParse.Rejected -> _zendoMessage.value = parsed.message
                is BackupParse.Ok -> {
                    _backupPreview.value = BackupPreviewUi(fileNameOf(uri), parsed.contents)
                    _zendoMessage.value = null
                }
            }
        }
    }

    fun cancelBackupRestore() { _backupPreview.value = null }

    /**
     * Replaces the live action, highlights and tasks with the backup's; day records
     * are upserted by date, so dates the backup does not contain are kept. Package
     * names and calendar ids are not part of a backup and are never touched.
     */
    fun confirmBackupRestore() {
        val preview = _backupPreview.value ?: return
        val c = preview.contents
        todayActionStore.replace(c.actionTitle, c.actionDone, c.actionOrigin, c.actionSourceId)
        highlightStore.replaceAll(c.highlights)
        taskStore.replaceAll(c.tasks)
        historyStore.upsertAll(c.days)
        settingsStore.update {
            it.copy(
                theme = c.prefs.theme,
                layout = c.prefs.layout,
                clock24h = c.prefs.clock24h,
                reduceMotion = c.prefs.reduceMotion,
                swipeUpEnabled = c.prefs.swipeUpEnabled,
                pagingEnabled = c.prefs.pagingEnabled,
                monochromeIcons = c.prefs.monochromeIcons,
                pinLimit = c.prefs.pinLimit,
                screenTimeEnabled = c.prefs.screenTimeEnabled,
                screenTimeHidden = c.prefs.screenTimeHidden,
                pauseEnabled = c.prefs.pauseEnabled,
                pauseMessage = c.prefs.pauseMessage,
                windowEnabled = c.prefs.windowEnabled,
                windowStartMinute = c.prefs.windowStartMinute,
                windowEndMinute = c.prefs.windowEndMinute,
            )
        }
        if (!c.prefs.screenTimeEnabled) {
            _usageAccess.value = false
            _usageEntries.value = emptyList()
        }
        _backupPreview.value = null
        _zendoMessage.value = "Backup restored."
    }

    /**
     * Writes everything local to the picked file: action, highlights, tasks, day
     * records and behaviour preferences. No package names, no calendar ids, no
     * accounts — a backup that leaked those would be the kind of file this project
     * exists to avoid.
     */
    fun exportBackupTo(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val s = settingsStore.settings.value
            val actionTitle = todayActionStore.title.value
            val contents = BackupContents(
                actionTitle = actionTitle,
                actionDone = actionTitle.isNotBlank() && todayActionStore.done.value,
                actionOrigin = todayActionStore.origin.value,
                actionSourceId = todayActionStore.sourceId.value,
                highlights = highlightStore.highlights.value,
                tasks = taskStore.tasks.value,
                days = historyStore.days.value,
                prefs = BackupPrefs(
                    theme = s.theme,
                    layout = s.layout,
                    clock24h = s.clock24h,
                    reduceMotion = s.reduceMotion,
                    swipeUpEnabled = s.swipeUpEnabled,
                    pagingEnabled = s.pagingEnabled,
                    monochromeIcons = s.monochromeIcons,
                    pinLimit = s.pinLimit,
                    screenTimeEnabled = s.screenTimeEnabled,
                    screenTimeHidden = s.screenTimeHidden,
                    pauseEnabled = s.pauseEnabled,
                    pauseMessage = s.pauseMessage,
                    windowEnabled = s.windowEnabled,
                    windowStartMinute = s.windowStartMinute,
                    windowEndMinute = s.windowEndMinute,
                ),
            )
            val raw = BridgeJson.encodeBackup(contents, BuildConfig.VERSION_NAME, System.currentTimeMillis())
            _historyMessage.value = if (writeTextFile(uri, raw)) {
                "Backup saved."
            } else {
                "Couldn't write the backup file."
            }
        }
    }

    // ---- File helpers (SAF documents the user picked; local only) ---------------------

    private fun readTextFile(uri: Uri): String? = try {
        getApplication<Application>().contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader().use { reader ->
                val text = reader.readText()
                if (text.length > BridgeJson.MAX_RAW_CHARS) null else text
            }
        }
    } catch (_: Exception) {
        null
    }

    private fun writeTextFile(uri: Uri, text: String): Boolean = try {
        getApplication<Application>().contentResolver.openOutputStream(uri)?.use { stream ->
            stream.bufferedWriter().use { it.write(text) }
            true
        } ?: false
    } catch (_: Exception) {
        false
    }

    private fun fileNameOf(uri: Uri): String =
        uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "file"

    suspend fun bitmapFor(target: LaunchTarget, sizePx: Int): Bitmap? =
        withContext(Dispatchers.IO) { appRepository.bitmapFor(target, sizePx) }
}
