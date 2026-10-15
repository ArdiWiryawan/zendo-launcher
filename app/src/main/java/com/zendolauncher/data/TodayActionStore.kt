package com.zendolauncher.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one primary action for today. Deliberately a single record, not a list:
 * it is the day's headline, distinct from Highlights (notes) and Tasks (list).
 * Persisted across restarts; never auto-cleared (no midnight wipe — the user
 * replaces it when the day's priority changes).
 */
class TodayActionStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _title = MutableStateFlow(prefs.getString(KEY_TITLE, "").orEmpty())
    val title: StateFlow<String> = _title.asStateFlow()

    private val _done = MutableStateFlow(prefs.getBoolean(KEY_DONE, false))
    val done: StateFlow<Boolean> = _done.asStateFlow()

    /** Saves the action. Blank title is refused (returns false); nothing is lost. */
    fun save(title: String): Boolean {
        val t = title.trim()
        if (t.isEmpty()) return false
        prefs.edit {
            putString(KEY_TITLE, t)
            // A new action starts undone; re-saving the same title keeps its state.
            if (t != _title.value) putBoolean(KEY_DONE, false)
        }
        if (t != _title.value) _done.value = false
        _title.value = t
        return true
    }

    fun setDone(done: Boolean) {
        prefs.edit { putBoolean(KEY_DONE, done) }
        _done.value = done
    }

    companion object {
        private const val PREFS = "launcher_today_action"
        private const val KEY_TITLE = "today_action_title"
        private const val KEY_DONE = "today_action_done"
    }
}
