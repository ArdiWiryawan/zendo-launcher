package com.zendolauncher.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.zendolauncher.core.HomeLayout
import com.zendolauncher.core.HomeLayoutCodec
import com.zendolauncher.core.MindfulGate
import com.zendolauncher.core.PageConfig
import com.zendolauncher.core.PageType
import com.zendolauncher.ui.theme.BackdropStyle
import com.zendolauncher.ui.theme.ScrimStrength
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The launcher has two calm moods: Soft Matte Charcoal (DARK) and Warm Sage Paper (LIGHT).
 */
enum class ThemeMode { LIGHT, DARK }

/**
 * Two Home experiences:
 *  - QUIET: ultra-clean single screen (clock, calendar glance, clean app list, search).
 *  - CALM_CANVAS: structured multi-page pager (Home, Plan, Today, Usage, Note).
 */
enum class HomeMode { QUIET, CALM_CANVAS }

/** Horizontal alignment for Quiet Home content. */
enum class HomeAlignment { CENTER, LEFT }

/** Casing for app labels on Home. */
enum class LabelCase { LOWERCASE, ORIGINAL }

/**
 * All launcher preferences. Local only — nothing here is ever uploaded.
 * Stored in SharedPreferences: a handful of keys and one ordered string list do not
 * justify a database or DataStore.
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<LauncherSettings> = _settings.asStateFlow()

    fun read(): LauncherSettings = LauncherSettings(
        theme = prefs.getString(KEY_THEME, null)
            ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.DARK,
        favourites = prefs.getString(KEY_FAVOURITES, "").orEmpty().toKeyList(),
        hidden = prefs.getString(KEY_HIDDEN, "").orEmpty().toKeyList().toSet(),
        onboardingSeen = prefs.getBoolean(KEY_ONBOARDING_SEEN, false),
        distract = prefs.getStringSet(KEY_DISTRACT, null)?.toSet() ?: emptySet(),
        pauseEnabled = prefs.getBoolean(KEY_PAUSE, false),
        pauseMessage = prefs.getString(KEY_PAUSE_MESSAGE, null)?.takeIf { it.isNotBlank() }
            ?: DEFAULT_PAUSE_MESSAGE,
        windowEnabled = prefs.getBoolean(KEY_WINDOW_ENABLED, false),
        windowStartMinute = prefs.getInt(KEY_WINDOW_START, DEFAULT_WINDOW_START),
        windowEndMinute = prefs.getInt(KEY_WINDOW_END, DEFAULT_WINDOW_END),
        reduceMotion = prefs.getBoolean(KEY_REDUCE_MOTION, false),
        shownCalendarIds = prefs.getStringSet(KEY_SHOWN_CALENDARS, null)?.mapNotNull { it.toLongOrNull() }?.toSet()
            ?: emptySet(),
        calendarAsked = prefs.getBoolean(KEY_CALENDAR_ASKED, false),
        calendarGoogleOnly = prefs.getBoolean(KEY_CALENDAR_GOOGLE_ONLY, false),
        // Calendar selection became explicit in 0.5.0: empty used to mean "all visible".
        // An install with stored preferences but no schema key predates that change and
        // must be migrated once (see LauncherViewModel.settleCalendarSelection); a fresh
        // install starts explicit, because there is no earlier view to preserve.
        calendarSelectionSchema = if (prefs.contains(KEY_CALENDAR_SCHEMA)) {
            prefs.getBoolean(KEY_CALENDAR_SCHEMA, false)
        } else {
            prefs.all.keys.isEmpty()
        },
        // Migration runs here, on the read path, because this is the only place the store
        // ever learns that no layout has been written yet. It is idempotent: once a layout
        // is stored, the legacy keys are not consulted again. See migrateHome.
        home = readHome(),
        homeMode = prefs.getString(KEY_HOME_MODE, null)
            ?.let { name -> HomeMode.entries.firstOrNull { it.name == name } } ?: HomeMode.QUIET,
        homeAlignment = prefs.getString(KEY_HOME_ALIGNMENT, null)
            ?.let { name -> HomeAlignment.entries.firstOrNull { it.name == name } } ?: HomeAlignment.CENTER,
        labelCase = prefs.getString(KEY_LABEL_CASE, null)
            ?.let { name -> LabelCase.entries.firstOrNull { it.name == name } } ?: LabelCase.LOWERCASE,
        backdropStyle = prefs.getString(KEY_BACKDROP_STYLE, null)
            ?.let { name -> BackdropStyle.entries.firstOrNull { it.name == name } } ?: BackdropStyle.MATTE,
        scrimStrength = prefs.getString(KEY_SCRIM_STRENGTH, null)
            ?.let { name -> ScrimStrength.entries.firstOrNull { it.name == name } } ?: ScrimStrength.BALANCED,
        backdropBlur = prefs.getBoolean(KEY_BACKDROP_BLUR, false),
        showCalendarGlance = prefs.getBoolean(KEY_SHOW_CALENDAR_GLANCE, true),
        showUsageOnHome = prefs.getBoolean(KEY_SHOW_USAGE_ON_HOME, true),
        pauseDelaySeconds = prefs.getInt(KEY_PAUSE_DELAY_SECONDS, MindfulGate.DEFAULT_DELAY_SECONDS)
            .let { if (it in MindfulGate.DELAY_OPTIONS_SECONDS) it else MindfulGate.DEFAULT_DELAY_SECONDS },
        gateTimeboxEnabled = prefs.getBoolean(KEY_GATE_TIMEBOX_ENABLED, true),
    )

    fun update(transform: (LauncherSettings) -> LauncherSettings) {
        val next = transform(_settings.value)
        prefs.edit {
            putString(KEY_THEME, next.theme.name)
            putString(KEY_FAVOURITES, next.favourites.joinToString(SEPARATOR))
            putString(KEY_HIDDEN, next.hidden.joinToString(SEPARATOR))
            putBoolean(KEY_ONBOARDING_SEEN, next.onboardingSeen)
            putStringSet(KEY_DISTRACT, HashSet(next.distract))
            putBoolean(KEY_PAUSE, next.pauseEnabled)
            putString(KEY_PAUSE_MESSAGE, next.pauseMessage)
            putBoolean(KEY_WINDOW_ENABLED, next.windowEnabled)
            // Stored raw. Normalising here would silently move a mistyped entry on the
            // next unrelated settings write; the gate clamps on read instead.
            putInt(KEY_WINDOW_START, next.windowStartMinute)
            putInt(KEY_WINDOW_END, next.windowEndMinute)
            putBoolean(KEY_REDUCE_MOTION, next.reduceMotion)
            putStringSet(KEY_SHOWN_CALENDARS, HashSet(next.shownCalendarIds.map { it.toString() }))
            putBoolean(KEY_CALENDAR_ASKED, next.calendarAsked)
            putBoolean(KEY_CALENDAR_GOOGLE_ONLY, next.calendarGoogleOnly)
            putBoolean(KEY_CALENDAR_SCHEMA, next.calendarSelectionSchema)
            putString(KEY_HOME_PAGES, HomeLayoutCodec.encode(next.home))
            putString(KEY_HOME_ACTIVE_PAGE, next.home.activePageId.orEmpty())
            putInt(KEY_HOME_PAGES_VERSION, HOME_PAGES_VERSION)
            putString(KEY_HOME_MODE, next.homeMode.name)
            putString(KEY_HOME_ALIGNMENT, next.homeAlignment.name)
            putString(KEY_LABEL_CASE, next.labelCase.name)
            putString(KEY_BACKDROP_STYLE, next.backdropStyle.name)
            putString(KEY_SCRIM_STRENGTH, next.scrimStrength.name)
            putBoolean(KEY_BACKDROP_BLUR, next.backdropBlur)
            putBoolean(KEY_SHOW_CALENDAR_GLANCE, next.showCalendarGlance)
            putBoolean(KEY_SHOW_USAGE_ON_HOME, next.showUsageOnHome)
            putInt(KEY_PAUSE_DELAY_SECONDS, next.pauseDelaySeconds)
            putBoolean(KEY_GATE_TIMEBOX_ENABLED, next.gateTimeboxEnabled)
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
     *
     * A stored layout is returned as the user arranged it. In particular the read path
     * never inserts a page: an earlier build re-added the Plan & Focus page whenever it
     * was missing, which meant a deleted page came back the next time the launcher
     * started.
     */
    private fun readHome(): HomeLayout = HomeLayout.sanitize(
        HomeLayoutCodec.decode(prefs.getString(KEY_HOME_PAGES, null))
            ?.copy(activePageId = prefs.getString(KEY_HOME_ACTIVE_PAGE, null))
            ?: migratedHome(),
    )

    /**
     * The layout to use when nothing has been stored yet.
     *
     * On a fresh install this is [HomeLayout.DEFAULT] — one page holding the apps you
     * pinned. On an install that already had favourites, there is something to lose, so
     * it is carried onto that page — in the returned layout, never by writing on a read.
     * That is the whole migration: the legacy keys are read once, folded into a page, and
     * left on disk untouched. A downgrade therefore still finds its `favourites` list
     * exactly as it left it, and the next write of any setting persists the migrated
     * layout, after which the legacy keys are simply never consulted again.
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

    /**
     * Pins or unpins [key].
     *
     * Returns false only when the pin was refused because the target page already holds
     * [MAX_PINS_PER_PAGE] apps — the caller turns that into the one sentence the user
     * needs ("remove a pin first") instead of a silent no-op.
     */
    fun toggleFavourite(key: String): Boolean {
        var changed = false
        update { current ->
            val layout = current.home
            // Pinning from the drawer is an arrangement of Home, so it lands on a page that can
            // actually show it: the active page when it is the apps page, the first apps page
            // otherwise, and a newly created apps page when there is none at all.
            val pageId = layout.pinTargetPageId()
            if (pageId == null) {
                val withPage = layout.withAppsPage()
                changed = true
                current.copy(home = withPage.addItem(withPage.pages.last().id, key))
            } else {
                val page = layout.page(pageId) ?: return@update current
                if (key in page.config.itemKeys) {
                    changed = true
                    current.copy(home = layout.removeItem(pageId, key))
                } else {
                    if (page.config.itemKeys.size >= MAX_PINS_PER_PAGE) return@update current
                    // The active page follows the pin, so a pin made from the drawer is
                    // visible on the page the user returns to rather than appearing to do
                    // nothing.
                    changed = true
                    current.copy(home = layout.addItem(pageId, key).setActivePage(pageId))
                }
            }
        }
        return changed
    }

    /**
     * Ends the legacy "empty means all calendars" era, once, when the calendars are
     * first known. [ids] is the previous view written down for an upgrade; null just
     * records that the new explicit semantics already apply (a fresh install, or a
     * selection that was already explicit).
     */
    fun materializeCalendarSelection(ids: Set<Long>?) = update {
        if (ids == null) {
            it.copy(calendarSelectionSchema = true)
        } else {
            it.copy(shownCalendarIds = ids, calendarGoogleOnly = false, calendarSelectionSchema = true)
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
        // Re-typing keeps the item list, so an Apps page switched to Note and back finds
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
        private const val KEY_FAVOURITES = "favourites"
        private const val KEY_HIDDEN = "hidden"
        private const val KEY_ONBOARDING_SEEN = "onboarding_seen"
        private const val KEY_DISTRACT = "distract_set"
        private const val KEY_PAUSE = "pause_enabled"
        private const val KEY_PAUSE_MESSAGE = "pause_message"
        private const val KEY_WINDOW_ENABLED = "access_window_enabled"
        private const val KEY_WINDOW_START = "access_window_start_minute"
        private const val KEY_WINDOW_END = "access_window_end_minute"
        private const val KEY_HOME_PAGES = "home_pages"
        private const val KEY_HOME_ACTIVE_PAGE = "home_active_page_id"
        private const val KEY_HOME_PAGES_VERSION = "home_pages_version"
        private const val KEY_REDUCE_MOTION = "reduce_motion"
        private const val KEY_SHOWN_CALENDARS = "shown_calendar_ids"
        private const val KEY_CALENDAR_ASKED = "calendar_asked"
        private const val KEY_CALENDAR_GOOGLE_ONLY = "calendar_google_only"
        private const val KEY_CALENDAR_SCHEMA = "calendar_selection_schema"
        private const val KEY_HOME_MODE = "home_mode"
        private const val KEY_HOME_ALIGNMENT = "home_alignment"
        private const val KEY_LABEL_CASE = "label_case"
        private const val KEY_BACKDROP_STYLE = "backdrop_style"
        private const val KEY_SCRIM_STRENGTH = "scrim_strength"
        private const val KEY_BACKDROP_BLUR = "backdrop_blur"
        private const val KEY_SHOW_CALENDAR_GLANCE = "show_calendar_glance"
        private const val KEY_SHOW_USAGE_ON_HOME = "show_usage_on_home"
        private const val KEY_PAUSE_DELAY_SECONDS = "pause_delay_seconds"
        private const val KEY_GATE_TIMEBOX_ENABLED = "gate_timebox_enabled"

        /** Advisory, written beside the blob so a future change can tell old from malformed. */
        const val HOME_PAGES_VERSION = 1

        /**
         * Pinned apps allowed on one page. Six is not a user choice any more: it is the
         * number that fits the shortest supported screen as two calm columns, and a
         * setting for it was one more row that only ever said "the cap is the cap".
         */
        const val MAX_PINS_PER_PAGE = 6

        /** Default pause-dialog reminder, used until the user writes their own. */
        const val DEFAULT_PAUSE_MESSAGE = "Take a breath. Open it anyway?"

        /** 22:00 — a default the user is expected to change, not a recommendation. */
        const val DEFAULT_WINDOW_START = 22 * 60
        /** 02:00 — wraps past midnight, so the default exercises the wrap-around branch. */
        const val DEFAULT_WINDOW_END = 2 * 60
    }
}

data class LauncherSettings(
    /** Light or dark. Defaults to dark: the calmest first impression of this launcher. */
    val theme: ThemeMode = ThemeMode.DARK,
    val favourites: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val onboardingSeen: Boolean = false,
    val distract: Set<String> = emptySet(),
    val pauseEnabled: Boolean = false,
    /** Reminder line shown in the intentional-pause dialog. User-written. */
    val pauseMessage: String = SettingsStore.DEFAULT_PAUSE_MESSAGE,
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
    /** When on, programmatic page moves jump instead of animating. Accessibility, not taste. */
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
    /**
     * When on, the agenda reads Google-account calendars only. Local and work
     * calendars are never silently mixed in once the user chose Google.
     */
    val calendarGoogleOnly: Boolean = false,
    /**
     * True once the calendar selection is known to be explicit: empty means "none
     * chosen", not "all visible". False on an install that predates the change and
     * has not been migrated yet; the view model migrates it once, when calendars
     * are first readable.
     */
    val calendarSelectionSchema: Boolean = false,
    val homeMode: HomeMode = HomeMode.QUIET,
    val homeAlignment: HomeAlignment = HomeAlignment.CENTER,
    val labelCase: LabelCase = LabelCase.LOWERCASE,
    val backdropStyle: BackdropStyle = BackdropStyle.MATTE,
    val scrimStrength: ScrimStrength = ScrimStrength.BALANCED,
    val backdropBlur: Boolean = false,
    val showCalendarGlance: Boolean = true,
    val showUsageOnHome: Boolean = true,
    val pauseDelaySeconds: Int = MindfulGate.DEFAULT_DELAY_SECONDS,
    val gateTimeboxEnabled: Boolean = true,
)
