package com.zendolauncher.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one line the Focus page holds: what this focus session is for.
 *
 * A separate preferences file from [SettingsStore] on purpose. Settings are read
 * synchronously before the first frame (see `LauncherActivity.onCreate`, which needs the
 * theme before the window exists); a note the user is mid-way through typing is not, and
 * a keystroke must never rewrite the settings blob. Two files also mean the note survives
 * a settings reset and a settings write can never drop it.
 *
 * This is storage and nothing else. There is no timer, no session log and no history —
 * the Focus page states that in words rather than implying otherwise with a dead control.
 */
class FocusNoteStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _note = MutableStateFlow(read())
    val note: StateFlow<String> = _note.asStateFlow()

    fun read(): String = prefs.getString(KEY_NOTE, "").orEmpty()

    /**
     * Stores the note. Blank is stored as blank rather than as absent, so clearing a note
     * is an edit the user made and not a value that mysteriously returns on next read.
     */
    fun write(note: String) {
        prefs.edit { putString(KEY_NOTE, note) }
        _note.value = note
    }

    companion object {
        private const val PREFS = "launcher_focus"
        private const val KEY_NOTE = "focus_note"
    }
}
