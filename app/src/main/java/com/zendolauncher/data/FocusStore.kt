package com.zendolauncher.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.zendolauncher.core.FocusPhase
import com.zendolauncher.core.FocusRecord
import com.zendolauncher.core.FocusSession
import com.zendolauncher.core.pause
import com.zendolauncher.core.resume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Focus timer state + completed-session history. The live session is
 * timestamp-based (`FocusSession`), so recovery is a pure function applied on
 * every read: a segment that ended while the process was dead is recorded
 * exactly once, and a live one resumes untouched.
 */
class FocusStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _session = MutableStateFlow(readSession())
    val session: StateFlow<FocusSession> = _session.asStateFlow()

    private val _history = MutableStateFlow(readHistory())
    val history: StateFlow<List<FocusRecord>> = _history.asStateFlow()

    init {
        // Recover once at startup: process death mid-session must not lose or
        // double-count the block.
        recover()
    }

    fun recover(nowMillis: Long = System.currentTimeMillis()) {
        val (session, history) = FocusSession.recover(_session.value, _history.value, nowMillis)
        persist(session, history)
    }

    fun startWork(workMinutes: Int, breakMinutes: Int, nowMillis: Long = System.currentTimeMillis()) {
        val w = workMinutes.coerceIn(1, 180)
        val b = breakMinutes.coerceIn(0, 60)
        persist(FocusSession.startWork(w, b, nowMillis), _history.value)
    }

    fun startBreak(nowMillis: Long = System.currentTimeMillis()) {
        persist(FocusSession.startBreak(_session.value, nowMillis), _history.value)
    }

    fun pause(nowMillis: Long = System.currentTimeMillis()) {
        val current = _session.value
        if (current.phase != FocusPhase.WORK && current.phase != FocusPhase.BREAK) return
        persist(current.pause(nowMillis), _history.value)
    }

    fun resume(nowMillis: Long = System.currentTimeMillis()) {
        if (_session.value.phase != FocusPhase.PAUSED) return
        persist(_session.value.resume(nowMillis), _history.value)
    }

    /**
     * Finishes the segment. Worked time counts once here; a naturally ended
     * segment is recorded by [recover] instead, so the two paths can never
     * both claim the same block.
     */
    fun finish(naturally: Boolean = false, nowMillis: Long = System.currentTimeMillis()) {
        val current = _session.value
        if (current.phase == FocusPhase.IDLE) return
        val remaining = FocusSession.remaining(current, nowMillis) ?: 0L
        val worked = (current.segmentLengthMillis - remaining).coerceAtLeast(0L)
        val start = current.segmentStartMillis ?: nowMillis
        val record = FocusRecord(
            startedAtMillis = start,
            plannedMinutes = if (current.onBreak) current.breakMinutes else current.workMinutes,
            workedMillis = worked,
            finishedNaturally = naturally,
        )
        persist(
            current.copy(
                phase = FocusPhase.IDLE,
                segmentStartMillis = null,
                segmentLengthMillis = 0L,
                pausedTotalMillis = 0L,
                pauseStartedAtMillis = null,
                onBreak = false,
            ),
            (_history.value + record).takeLast(MAX_HISTORY),
        )
    }

    fun clearHistory() {
        persist(_session.value, emptyList())
    }

    private fun persist(session: FocusSession, history: List<FocusRecord>) {
        prefs.edit {
            putString(KEY_SESSION, encodeSession(session))
            putString(KEY_HISTORY, encodeHistory(history.takeLast(MAX_HISTORY)))
        }
        _session.value = session
        _history.value = history.takeLast(MAX_HISTORY)
    }

    private fun readSession(): FocusSession = decodeSession(prefs.getString(KEY_SESSION, null))

    private fun readHistory(): List<FocusRecord> =
        decodeHistory(prefs.getString(KEY_HISTORY, null)).takeLast(MAX_HISTORY)

    companion object {
        private const val PREFS = "launcher_focus_sessions"
        private const val KEY_SESSION = "focus_session"
        private const val KEY_HISTORY = "focus_history"
        const val MAX_HISTORY = 30

        fun encodeSession(s: FocusSession): String = JSONObject()
            .put("phase", s.phase.name)
            .put("workMinutes", s.workMinutes)
            .put("breakMinutes", s.breakMinutes)
            .put("segmentStart", s.segmentStartMillis ?: -1L)
            .put("segmentLength", s.segmentLengthMillis)
            .put("pausedTotal", s.pausedTotalMillis)
            .put("pauseStartedAt", s.pauseStartedAtMillis ?: -1L)
            .put("onBreak", s.onBreak)
            .toString()

        fun decodeSession(raw: String?): FocusSession {
            if (raw.isNullOrBlank()) return FocusSession()
            return try {
                val o = JSONObject(raw)
                val phase = runCatching {
                    FocusPhase.valueOf(o.optString("phase", "IDLE"))
                }.getOrDefault(FocusPhase.IDLE)
                FocusSession(
                    phase = phase,
                    workMinutes = o.optInt("workMinutes", 25).coerceIn(1, 180),
                    breakMinutes = o.optInt("breakMinutes", 5).coerceIn(0, 60),
                    segmentStartMillis = o.optLong("segmentStart", -1L).takeIf { it >= 0 },
                    segmentLengthMillis = o.optLong("segmentLength", 0L).coerceAtLeast(0L),
                    pausedTotalMillis = o.optLong("pausedTotal", 0L).coerceAtLeast(0L),
                    pauseStartedAtMillis = o.optLong("pauseStartedAt", -1L).takeIf { it >= 0 },
                    onBreak = o.optBoolean("onBreak", false),
                )
            } catch (_: Exception) {
                FocusSession()
            }
        }

        fun encodeHistory(history: List<FocusRecord>): String {
            val array = JSONArray()
            history.forEach { r ->
                array.put(
                    JSONObject()
                        .put("startedAt", r.startedAtMillis)
                        .put("plannedMinutes", r.plannedMinutes)
                        .put("workedMillis", r.workedMillis)
                        .put("natural", r.finishedNaturally),
                )
            }
            return array.toString()
        }

        fun decodeHistory(raw: String?): List<FocusRecord> {
            if (raw.isNullOrBlank()) return emptyList()
            return try {
                val array = JSONArray(raw)
                val out = ArrayList<FocusRecord>(array.length())
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    out += FocusRecord(
                        startedAtMillis = o.optLong("startedAt", 0L),
                        plannedMinutes = o.optInt("plannedMinutes", 0),
                        workedMillis = o.optLong("workedMillis", 0L),
                        finishedNaturally = o.optBoolean("natural", false),
                    )
                }
                out
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
