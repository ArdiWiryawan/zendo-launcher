package com.zendolauncher.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.zendolauncher.core.HomeLayout
import com.zendolauncher.core.HomeLayoutCodec
import com.zendolauncher.core.PageConfig
import com.zendolauncher.core.PageType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class LayoutMode { TEXT, ICON_TEXT }

/**
 * All launcher preferences. Local only — nothing here is ever uploaded.
 * Stored in SharedPreferences: a handful of keys and two ordered string lists do not
 * justify a database or DataStore.
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<LauncherSettings> = _settings.asStateFlow()

    fun read(): LauncherSettings = LauncherSettings(
        theme = prefs.getString(KEY_THEME, null)
            ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.SYSTEM,
        layout = prefs.getString(KEY_LAYOUT, null)
            ?.let { name -> LayoutMode.entries.firstOrNull { it.name == name } } ?: LayoutMode.TEXT,
        favourites = prefs.getString(KEY_FAVOURITES, "").orEmpty().toKeyList(),
        hidden = prefs.getString(KEY_HIDDEN, "").orEmpty().toKeyList().toSet(),
        todayEnabled = prefs.getBoolean(KEY_TODAY, true),
        clock24h = prefs.getBoolean(KEY_CLOCK_24H, true),
        zendoEnabled = prefs.getBoolean(KEY_ZENDO, false),
        onboardingSeen = prefs.getBoolean(KEY_ONBOARDING_SEEN, false),
        distract = prefs.getStringSet(KEY_DISTRACT, null)?.toSet() ?: emptySet(),
        pauseEnabled = prefs.getBoolean(KEY_PAUSE, false),
        windowEnabled = prefs.getBoolean(KEY_WINDOW_ENABLED, false),
        windowStartMinute = prefs.getInt(KEY_WINDOW_START, DEFAULT_WINDOW_START),
        windowEndMinute = prefs.getInt(KEY_WINDOW_END, DEFAULT_WINDOW_END),
        swipeUpEnabled = prefs.getBoolean(KEY_SWIPE_UP, true),
        pagingEnabled = prefs.getBoolean(KEY_PAGING, true),
        reduceMotion = prefs.getBoolean(KEY_REDUCE_MOTION, false),
        shownCalendarIds = prefs.getStringSet(KEY_SHOWN_CALENDARS, null)?.mapNotNull { it.toLongOrNull() }?.toSet()
            ?: emptySet(),
        calendarAsked = prefs.getBoolean(KEY_CALENDAR_ASKED, false),
        // Migration runs here, on the read path, because this is the only place the store
        // ever learns that no layout has been written yet. It is idempotent: once a layout
        // is stored, the legacy keys are not consulted again. See migrateHome.
        home = readHome(),
    )

    fun update(transform: (LauncherSettings) -> LauncherSettings) {
        val next = transform(_settings.value)
        prefs.edit {
            putString(KEY_THEME, next.theme.name)
            putString(KEY_LAYOUT, next.layout.name)
            putString(KEY_FAVOURITES, next.favourites.joinToString(SEPARATOR))
            putString(KEY_HIDDEN, next.hidden.joinToString(SEPARATOR))
            putBoolean(KEY_TODAY, next.todayEnabled)
            putBoolean(KEY_CLOCK_24H, next.clock24h)
            putBoolean(KEY_ZENDO, next.zendoEnabled)
            putBoolean(KEY_ONBOARDING_SEEN, next.onboardingSeen)
            putStringSet(KEY_DISTRACT, HashSet(next.distract))
            putBoolean(KEY_PAUSE, next.pauseEnabled)
            putBoolean(KEY_WINDOW_ENABLED, next.windowEnabled)
            // Stored raw. Normalising here would silently move a mistyped entry on the
            // next unrelated settings write; the gate clamps on read instead.
            putInt(KEY_WINDOW_START, next.windowStartMinute)
            putInt(KEY_WINDOW_END, next.windowEndMinute)
            putBoolean(KEY_SWIPE_UP, next.swipeUpEnabled)
            putBoolean(KEY_PAGING, next.pagingEnabled)
            putBoolean(KEY_REDUCE_MOTION, next.reduceMotion)
            putStringSet(KEY_SHOWN_CALENDARS, HashSet(next.shownCalendarIds.map { it.toString() }))
            putBoolean(KEY_CALENDAR_ASKED, next.calendarAsked)
            putString(KEY_HOME_PAGES, HomeLayoutCodec.encode(next.home))
            putString(KEY_HOME_ACTIVE_PAGE, next.home.activePageId.orEmpty())
            putInt(KEY_HOME_PAGES_VERSION, HOME_PAGES_VERSION)
        }
        _settings.value = next
    }

    /**
     * The stored Home layout, repaired by [HomeLayout.sanitize], or the migrated one on
     * first read after an update.
     *
     * `sanitize` runs on every read and after every mutation rather than at one call site,
     * which is what makes the store the single source of truth: no composable and no view
     * model has to know how to clamp a page count or repair a duplicate id.
     */
    private fun readHome(): HomeLayout = HomeLayout.sanitize(
        HomeLayoutCodec.decode(prefs.getString(KEY_HOME_PAGES, null))
            ?.copy(activePageId = prefs.getString(KEY_HOME_ACTIVE_PAGE, null))
            ?: migratedHome(),
    )

    /**
     * The layout to use when nothing has been stored yet.
     *
     * On a fresh install this is simply [HomeLayout.DEFAULT]. On an install that already
     * had favourites, there is something to lose, so it is carried onto the Launch page —
     * in the returned layout, never by writing on a read. That is the whole migration: the
     * legacy keys are read once, folded into a page, and left on disk untouched. A
     * downgrade to 0.2.3 therefore still finds its `favourites` list exactly as it left it,
     * and the next write of any setting persists the migrated layout, after which the
     * legacy keys are simply never consulted again.
     */
    private fun migratedHome(): HomeLayout {
        val legacyFavourites = prefs.getString(KEY_FAVOURITES, "").orEmpty().toKeyList()
        if (legacyFavourites.isEmpty()) return HomeLayout.DEFAULT

        val default = HomeLayout.DEFAULT
        val launchPage = default.pages.first { it.type == PageType.APPS }
        return default.updatePageConfig(
            launchPage.id,
            launchPage.config.copy(itemKeys = legacyFavourites),
        )
    }

    fun toggleFavourite(key: String) = update { current ->
        val layout = current.home
        // Pinning from the drawer is an arrangement of Home, so it lands on a page that can
        // actually show it: the active page when it is the apps page, the first apps page
        // otherwise, and a newly created apps page when there is none at all.
        val pageId = layout.pinTargetPageId()
        if (pageId == null) {
            val withPage = layout.withAppsPage()
            current.copy(home = withPage.addItem(withPage.pages.last().id, key))
        } else {
            val page = layout.page(pageId) ?: return@update current
            val home = if (key in page.config.itemKeys) {
                layout.removeItem(pageId, key)
            } else {
                // The active page follows the pin, so a pin made from the drawer is
                // visible on the page the user returns to rather than appearing to do
                // nothing.
                layout.addItem(pageId, key).setActivePage(pageId)
            }
            current.copy(home = home)
        }
    }

    fun toggleHidden(key: String) = update { current ->
        val hidden = if (key in current.hidden) current.hidden - key else current.hidden + key
        // A hidden app cannot also be pinned, on any page, or it would be invisible but
        // still ordered. Unhiding does not restore it: hiding dropped it, and only the
        // user knows where it belongs now.
        val home = if (key in hidden) current.home.removeItemEverywhere(key) else current.home
        current.copy(hidden = hidden, favourites = current.favourites - key, home = home)
    }

    /**
     * Moves a pinned app within the page that holds it. A key on no page is a no-op, since
     * there is no order to disturb.
     */
    fun moveFavourite(key: String, delta: Int) = update { current ->
        val page = current.home.pages.firstOrNull { key in it.config.itemKeys } ?: return@update current
        val index = page.config.itemKeys.indexOf(key)
        current.copy(home = current.home.moveItem(page.id, index, index + delta))
    }

    // ---- Home pages ----------------------------------------------------------------

    fun setActivePage(id: String) = update { it.copy(home = it.home.setActivePage(id)) }

    /** Adds a page after the active one. Returns its id, or null at [HomeLayout.MAX_PAGES]. */
    fun addPage(type: PageType = PageType.APPS, name: String? = null): String? {
        var newId: String? = null
        update { current ->
            val result = current.home.addPage(type, name) ?: return@update current
            newId = result.second
            current.copy(home = result.first.setActivePage(result.second))
        }
        return newId
    }

    fun removePage(id: String) = update { it.copy(home = it.home.removePage(id)) }

    fun renamePage(id: String, name: String) = update { it.copy(home = it.home.renamePage(id, name)) }

    fun movePage(id: String, toIndex: Int) = update { it.copy(home = it.home.movePage(id, toIndex)) }

    fun setPageType(id: String, type: PageType) = update { current ->
        // Re-typing keeps the item list, so a Launch page switched to Focus and back finds
        // its apps still there.
        current.copy(home = current.home.setType(id, type))
    }

    fun updatePageConfig(id: String, config: PageConfig) =
        update { it.copy(home = it.home.updatePageConfig(id, config)) }

    fun movePageItem(pageId: String, from: Int, to: Int) =
        update { it.copy(home = it.home.moveItem(pageId, from, to)) }

    private fun String.toKeyList(): List<String> =
        if (isEmpty()) emptyList() else split(SEPARATOR).filter { it.isNotBlank() }

    companion object {
        private const val PREFS = "launcher_settings"
        private const val SEPARATOR = "\n"
        private const val KEY_THEME = "theme"
        private const val KEY_LAYOUT = "layout"
        private const val KEY_FAVOURITES = "favourites"
        private const val KEY_HIDDEN = "hidden"
        private const val KEY_TODAY = "today_enabled"
        private const val KEY_CLOCK_24H = "clock_24h"
        private const val KEY_ZENDO = "zendo_enabled"
        private const val KEY_ONBOARDING_SEEN = "onboarding_seen"
        private const val KEY_DISTRACT = "distract_set"
        private const val KEY_PAUSE = "pause_enabled"
        private const val KEY_WINDOW_ENABLED = "access_window_enabled"
        private const val KEY_WINDOW_START = "access_window_start_minute"
        private const val KEY_WINDOW_END = "access_window_end_minute"
        private const val KEY_HOME_PAGES = "home_pages"
        private const val KEY_HOME_ACTIVE_PAGE = "home_active_page_id"
        private const val KEY_HOME_PAGES_VERSION = "home_pages_version"
        private const val KEY_SWIPE_UP = "swipe_up_enabled"
        private const val KEY_PAGING = "paging_enabled"
        private const val KEY_REDUCE_MOTION = "reduce_motion"
        private const val KEY_SHOWN_CALENDARS = "shown_calendar_ids"
        private const val KEY_CALENDAR_ASKED = "calendar_asked"

        /** Advisory, written beside the blob so a future change can tell old from malformed. */
        const val HOME_PAGES_VERSION = 1

        /** 22:00 — a default the user is expected to change, not a recommendation. */
        const val DEFAULT_WINDOW_START = 22 * 60
        /** 02:00 — wraps past midnight, so the default exercises the wrap-around branch. */
        const val DEFAULT_WINDOW_END = 2 * 60
    }
}

data class LauncherSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val layout: LayoutMode = LayoutMode.TEXT,
    val favourites: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val todayEnabled: Boolean = true,
    val clock24h: Boolean = true,
    val zendoEnabled: Boolean = false,
    val onboardingSeen: Boolean = false,
    val distract: Set<String> = emptySet(),
    val pauseEnabled: Boolean = false,
    /**
     * Optional daily access window, minute-of-day local. Off by default and only ever
     * consulted by the pause gate — nothing polls it, so it is a launch-time check.
     */
    val windowEnabled: Boolean = false,
    val windowStartMinute: Int = SettingsStore.DEFAULT_WINDOW_START,
    val windowEndMinute: Int = SettingsStore.DEFAULT_WINDOW_END,
    /**
     * The Home layout: one to five ordered pages. Always sanitized on read and after every
     * mutation, so a consumer can trust the page count, the ids, the names and the active
     * id without checking any of them. [favourites] above is the pre-pages flat list and is
     * kept only so a downgrade still finds it; nothing reads it for display any more.
     */
    val home: HomeLayout = HomeLayout.DEFAULT,
    /** Swipe up on Home chrome opens the drawer. Enabled by default. */
    val swipeUpEnabled: Boolean = true,
    /** Horizontal swipe pages Home. Enabled by default. */
    val pagingEnabled: Boolean = true,
    /** When on, programmatic page moves jump instead of animating. */
    val reduceMotion: Boolean = false,
    /**
     * Calendar ids to show. Empty means all visible calendars. The selection UI writes
     * ids here; the query filters on them.
     */
    val shownCalendarIds: Set<Long> = emptySet(),
    /**
     * True once the calendar permission dialog has been answered (granted or denied).
     * Combined with the platform rationale flag this distinguishes a first ask from a
     * permanent denial, so a dead system dialog is never re-fired in a loop — the UI
     * offers the app Settings page instead.
     */
    val calendarAsked: Boolean = false,
)
