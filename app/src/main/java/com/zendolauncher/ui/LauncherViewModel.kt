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
    /** True only while the first enumeration is still running; see [LauncherViewModel.onStart]. */
    val isLoading: Boolean = false,
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

    val state: StateFlow<UiState> =
        combine(lists, prefs, zendo, _isLoading) { lists, prefs, zendo, isLoading ->
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
                isLoading = isLoading,
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

    fun openSettings() { _screen.value = Screen.SETTINGS }

    fun goHome() {
        _screen.value = Screen.HOME
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

    fun launch(target: LaunchTarget) = appLauncher.launch(target)

    fun toggleFavourite(key: String) = settingsStore.toggleFavourite(key)

    fun toggleHidden(key: String) = settingsStore.toggleHidden(key)

    fun moveFavourite(key: String, delta: Int) = settingsStore.moveFavourite(key, delta)

    fun setTheme(theme: ThemeMode) = settingsStore.update { it.copy(theme = theme) }

    fun setLayout(layout: LayoutMode) = settingsStore.update { it.copy(layout = layout) }

    fun setTodayEnabled(enabled: Boolean) = settingsStore.update { it.copy(todayEnabled = enabled) }

    fun setClock24h(enabled: Boolean) = settingsStore.update { it.copy(clock24h = enabled) }

    fun setZendoEnabled(enabled: Boolean) = settingsStore.update { it.copy(zendoEnabled = enabled) }

    suspend fun bitmapFor(target: LaunchTarget, sizePx: Int): Bitmap? =
        withContext(Dispatchers.IO) { appRepository.bitmapFor(target, sizePx) }
}
