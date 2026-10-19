package com.zendolauncher.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class WallpaperTarget { HOME, LOCK, BOTH }

/**
 * Remembers the last wallpaper choice. The actual pixels live with the system
 * (WallpaperManager); this is the launcher's record of what it asked for, and
 * whether the system call reported success — never a claim of success first.
 */
class WallpaperStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _selectedId = MutableStateFlow(prefs.getString(KEY_ID, null))
    val selectedId: StateFlow<String?> = _selectedId.asStateFlow()

    private val _appliedId = MutableStateFlow(prefs.getString(KEY_APPLIED_ID, null))

    /**
     * The template id of the last successful apply, or null until one succeeds. This is
     * what "match wallpaper" resolves its accent from: [selectedId] is the gallery's
     * highlight and changes on a tap, while this records what the device was actually
     * asked to show.
     */
    val appliedId: StateFlow<String?> = _appliedId.asStateFlow()

    private val _target = MutableStateFlow(
        prefs.getString(KEY_TARGET, null)?.let { runCatching { WallpaperTarget.valueOf(it) }.getOrNull() }
            ?: WallpaperTarget.BOTH,
    )
    val target: StateFlow<WallpaperTarget> = _target.asStateFlow()

    private val _lastAppliedMillis = MutableStateFlow(prefs.getLong(KEY_APPLIED_AT, 0L))
    val lastAppliedMillis: StateFlow<Long> = _lastAppliedMillis.asStateFlow()

    private val _lastSuccess = MutableStateFlow(prefs.getBoolean(KEY_SUCCESS, false))
    val lastSuccess: StateFlow<Boolean> = _lastSuccess.asStateFlow()

    fun select(id: String) {
        prefs.edit { putString(KEY_ID, id) }
        _selectedId.value = id
    }

    fun setTarget(target: WallpaperTarget) {
        prefs.edit { putString(KEY_TARGET, target.name) }
        _target.value = target
    }

    fun recordResult(success: Boolean) {
        val now = System.currentTimeMillis()
        prefs.edit {
            putLong(KEY_APPLIED_AT, now)
            putBoolean(KEY_SUCCESS, success)
        }
        _lastAppliedMillis.value = now
        _lastSuccess.value = success
    }

    /**
     * Records which design the apply actually sent to the system. Called only on a
     * confirmed apply (Applied or Partial), never on a tap in the gallery: the accent
     * that "match wallpaper" follows must be the one on the device, not the one merely
     * highlighted.
     */
    fun recordApplied(id: String) {
        prefs.edit { putString(KEY_APPLIED_ID, id) }
        _appliedId.value = id
    }

    companion object {
        private const val PREFS = "launcher_wallpaper"
        private const val KEY_ID = "wallpaper_template_id"
        private const val KEY_APPLIED_ID = "wallpaper_applied_id"
        private const val KEY_TARGET = "wallpaper_target"
        private const val KEY_APPLIED_AT = "wallpaper_applied_at"
        private const val KEY_SUCCESS = "wallpaper_last_success"
    }
}
