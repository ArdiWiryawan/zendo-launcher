package com.zendolauncher.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
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
        }
        _settings.value = next
    }

    fun toggleFavourite(key: String) = update { current ->
        val favourites = if (key in current.favourites) {
            current.favourites - key
        } else {
            current.favourites + key
        }
        current.copy(favourites = favourites)
    }

    fun toggleHidden(key: String) = update { current ->
        val hidden = if (key in current.hidden) current.hidden - key else current.hidden + key
        // A hidden app cannot also be a favourite, or it would be invisible but still ordered.
        val favourites = if (key in hidden) current.favourites - key else current.favourites
        current.copy(hidden = hidden, favourites = favourites)
    }

    fun moveFavourite(key: String, delta: Int) = update { current ->
        val index = current.favourites.indexOf(key)
        if (index < 0) return@update current
        val target = (index + delta).coerceIn(0, current.favourites.lastIndex)
        if (target == index) return@update current
        val reordered = current.favourites.toMutableList()
        reordered.removeAt(index)
        reordered.add(target, key)
        current.copy(favourites = reordered)
    }

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
)
