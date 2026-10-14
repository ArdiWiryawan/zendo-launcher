package com.zendolauncher.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zendolauncher.core.AppQuery
import com.zendolauncher.data.LayoutMode
import com.zendolauncher.data.LauncherSettings
import com.zendolauncher.data.SettingsStore
import com.zendolauncher.data.ThemeMode
import com.zendolauncher.model.LaunchTarget
import com.zendolauncher.model.ZendoToday
import com.zendolauncher.platform.AppLauncher
import com.zendolauncher.platform.AppRepository
import com.zendolauncher.platform.ShortcutRepository
import com.zendolauncher.platform.UnavailableZendoSource
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

enum class Screen { ONBOARDING, HOME, APPS, SETTINGS }

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
)

private data class Lists(
    val favourites: List<LaunchTarget>,
    val results: List<LaunchTarget>,
    val visibleApps: List<LaunchTarget>,
    val allApps: List<LaunchTarget>,
    val screen: Screen,
)

private data class Prefs(
    val settings: LauncherSettings,
    val query: String,
    val shortcutAccess: Boolean,
    val isDefaultHome: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val appRepository = AppRepository(application)
    private val shortcutRepository = ShortcutRepository(application)
    private val settingsStore = SettingsStore(application)
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

    private val favourites: Flow<List<LaunchTarget>> =
        combine(visibleApps, settingsStore.settings) { apps, settings ->
            AppQuery.favourites(settings.favourites, apps)
        }

    private val results: Flow<List<LaunchTarget>> =
        combine(visibleApps, _query) { apps, query -> AppQuery.search(apps, query) }

    private val zendo: Flow<ZendoToday?> = settingsStore.settings
        .map { it.zendoEnabled }
        .distinctUntilChanged()
        .flatMapLatest { enabled ->
            if (enabled) flow { emit(zendoSource.load()) } else flowOf(null)
        }

    private val lists: Flow<Lists> =
        combine(favourites, results, visibleApps, allApps, _screen) { favourites, results, visibleApps, allApps, screen ->
            Lists(favourites, results, visibleApps, allApps, screen)
        }

    private val prefs: Flow<Prefs> =
        combine(settingsStore.settings, _query, shortcutRepository.available, _isDefaultHome) { settings, query, shortcutAccess, isDefaultHome ->
            Prefs(settings, query, shortcutAccess, isDefaultHome)
        }

    private val base: Flow<UiState> =
        combine(lists, prefs, zendo, _isLoading, _undoHidden) { lists, prefs, zendo, isLoading, undoHidden ->
            UiState(
                screen = lists.screen,
                query = prefs.query,
                settings = prefs.settings,
                favourites = lists.favourites,
                results = lists.results,
                visibleApps = lists.visibleApps,
                shortcutAccess = prefs.shortcutAccess,
                isDefaultHome = prefs.isDefaultHome,
                zendo = zendo,
                allApps = lists.allApps,
                undoHidden = undoHidden,
                isLoading = isLoading,
                pendingPause = _pendingPause.value,
            )
        }

    val state: StateFlow<UiState> =
        combine(base, _pendingPause) { state, pendingPause ->
            state.copy(pendingPause = pendingPause)
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
        settingsReturn = from
        _screen.value = Screen.SETTINGS
    }

    /** Back from Settings returns to the drawer when entered from it, keeping the query. */
    fun backFromSettings() {
        _screen.value = settingsReturn
        settingsReturn = Screen.HOME
    }

    fun goHome() {
        _screen.value = Screen.HOME
        settingsReturn = Screen.HOME
        _query.value = ""
    }

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
     * ponytail: per-launch gate covers conscious entry up to daily use; upgrade to an
     * in-memory `allowUntilMs: Map<String, Long>` check here when timed access windows are needed.
     */
    fun launch(target: LaunchTarget) {
        val settings = settingsStore.settings.value
        if (settings.pauseEnabled && target.key in settings.distract) {
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

    fun moveFavourite(key: String, delta: Int) = settingsStore.moveFavourite(key, delta)

    fun setTheme(theme: ThemeMode) = settingsStore.update { it.copy(theme = theme) }

    fun setLayout(layout: LayoutMode) = settingsStore.update { it.copy(layout = layout) }

    fun setTodayEnabled(enabled: Boolean) = settingsStore.update { it.copy(todayEnabled = enabled) }

    fun setClock24h(enabled: Boolean) = settingsStore.update { it.copy(clock24h = enabled) }

    fun setZendoEnabled(enabled: Boolean) = settingsStore.update { it.copy(zendoEnabled = enabled) }

    fun setPauseEnabled(enabled: Boolean) = settingsStore.update { it.copy(pauseEnabled = enabled) }

    suspend fun bitmapFor(target: LaunchTarget, sizePx: Int): Bitmap? =
        withContext(Dispatchers.IO) { appRepository.bitmapFor(target, sizePx) }
}
