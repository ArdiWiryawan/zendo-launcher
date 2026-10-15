package com.zendolauncher.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.zendolauncher.core.Origins
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one primary action for today. Deliberately a single record, not a list:
 * it is the day's headline, distinct from Highlights (notes) and Tasks (list).
 * Persisted across restarts; never auto-cleared (no midnight wipe — the user
 * replaces it when the day's priority changes).
 *
 * [origin]/[sourceId] record where the current text came from, so a Zendo
 * snapshot can refresh what a previous import wrote without overwriting text
 * the user typed here.
 */
class TodayActionStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _title = MutableStateFlow(prefs.getString(KEY_TITLE, "").orEmpty())
    val title: StateFlow<String> = _title.asStateFlow()

    private val _done = MutableStateFlow(prefs.getBoolean(KEY_DONE, false))
    val done: StateFlow<Boolean> = _done.asStateFlow()

    private val _origin = MutableStateFlow(
        Origins.normalize(prefs.getString(KEY_ORIGIN, null)),
    )
    val origin: StateFlow<String> = _origin.asStateFlow()

    private val _sourceId = MutableStateFlow(
        prefs.getString(KEY_SOURCE_ID, null)?.takeIf { it.isNotBlank() },
    )
    val sourceId: StateFlow<String?> = _sourceId.asStateFlow()

    /** Saves the action. Blank title is refused (returns false); nothing is lost. */
    fun save(title: String, origin: String = Origins.LOCAL, sourceId: String? = null): Boolean {
        val t = title.trim()
        if (t.isEmpty()) return false
        val nextOrigin = Origins.normalize(origin)
        val nextSourceId = sourceId?.trim()?.takeIf { it.isNotEmpty() }
        prefs.edit {
            putString(KEY_TITLE, t)
            // A new action starts undone; re-saving the same title keeps its state.
            if (t != _title.value) putBoolean(KEY_DONE, false)
            putString(KEY_ORIGIN, nextOrigin)
            if (nextSourceId == null) remove(KEY_SOURCE_ID) else putString(KEY_SOURCE_ID, nextSourceId)
        }
        if (t != _title.value) _done.value = false
        _title.value = t
        _origin.value = nextOrigin
        _sourceId.value = nextSourceId
        return true
    }

    fun setDone(done: Boolean) {
        prefs.edit { putBoolean(KEY_DONE, done) }
        _done.value = done
    }

    /** Raw write used by backup restore; a blank title clears the action. */
    fun replace(title: String, done: Boolean, origin: String, sourceId: String? = null) {
        val t = title.trim()
        val nextOrigin = Origins.normalize(origin)
        val nextSourceId = sourceId?.trim()?.takeIf { it.isNotEmpty() }
        val nextDone = t.isNotEmpty() && done
        prefs.edit {
            putString(KEY_TITLE, t)
            putBoolean(KEY_DONE, nextDone)
            putString(KEY_ORIGIN, nextOrigin)
            if (nextSourceId == null) remove(KEY_SOURCE_ID) else putString(KEY_SOURCE_ID, nextSourceId)
        }
        _title.value = t
        _done.value = nextDone
        _origin.value = nextOrigin
        _sourceId.value = nextSourceId
    }

    companion object {
        private const val PREFS = "launcher_today_action"
        private const val KEY_TITLE = "today_action_title"
        private const val KEY_DONE = "today_action_done"
        private const val KEY_ORIGIN = "today_action_origin"
        private const val KEY_SOURCE_ID = "today_action_source_id"
    }
}
