package com.zendolauncher.core

enum class FocusPhase { IDLE, WORK, PAUSED, BREAK }

/** One finished focus block. */
data class FocusRecord(
    val startedAtMillis: Long,
    val plannedMinutes: Int,
    val workedMillis: Long,
    /** True when the timer ran out on its own rather than being finished early. */
    val finishedNaturally: Boolean,
)

/**
 * Timestamp-based focus state. Remaining time derives from wall-clock
 * timestamps, never from a UI counter — recomposition, navigation, and process
 * death cannot corrupt it. A pause freezes time by banking it; resume spends
 * from the same segment.
 */
data class FocusSession(
    val phase: FocusPhase = FocusPhase.IDLE,
    val workMinutes: Int = 25,
    val breakMinutes: Int = 5,
    /** Segment start in wall-clock millis (work or break). Null when idle/paused accounting differs. */
    val segmentStartMillis: Long? = null,
    val segmentLengthMillis: Long = 0L,
    /** Banked paused time inside the current segment. */
    val pausedTotalMillis: Long = 0L,
    /** When the current pause began, or null when not paused. */
    val pauseStartedAtMillis: Long? = null,
    /** True when the current segment is the break rather than the work block. */
    val onBreak: Boolean = false,
) {
    companion object {
        fun startWork(workMinutes: Int, breakMinutes: Int, nowMillis: Long): FocusSession =
            FocusSession(
                phase = FocusPhase.WORK,
                workMinutes = workMinutes,
                breakMinutes = breakMinutes,
                segmentStartMillis = nowMillis,
                segmentLengthMillis = workMinutes * 60_000L,
            )

        fun startBreak(from: FocusSession, nowMillis: Long): FocusSession =
            from.copy(
                phase = FocusPhase.BREAK,
                segmentStartMillis = nowMillis,
                segmentLengthMillis = from.breakMinutes * 60_000L,
                pausedTotalMillis = 0L,
                pauseStartedAtMillis = null,
                onBreak = true,
            )

        /** Millis left in the current segment at [nowMillis]. Null when idle. */
        fun remaining(session: FocusSession, nowMillis: Long): Long? {
            val start = session.segmentStartMillis ?: return null
            if (session.phase == FocusPhase.IDLE) return null
            val frozenAt = if (session.phase == FocusPhase.PAUSED) {
                session.pauseStartedAtMillis ?: return null
            } else {
                nowMillis
            }
            return session.segmentLengthMillis - (frozenAt - start - session.pausedTotalMillis)
        }

        /**
         * Process-recreation recovery. An ended work/break segment becomes a
         * history record exactly once; a live segment resumes untouched. Pure.
         */
        fun recover(
            session: FocusSession,
            history: List<FocusRecord>,
            nowMillis: Long,
        ): Pair<FocusSession, List<FocusRecord>> {
            if (session.phase != FocusPhase.WORK && session.phase != FocusPhase.BREAK) {
                return session to history
            }
            val left = remaining(session, nowMillis) ?: return session to history
            if (left > 0) return session to history
            val start = session.segmentStartMillis ?: return idle(session) to history
            val record = FocusRecord(
                startedAtMillis = start,
                plannedMinutes = if (session.phase == FocusPhase.WORK) session.workMinutes else session.breakMinutes,
                workedMillis = session.segmentLengthMillis,
                finishedNaturally = true,
            )
            return idle(session) to (history + record)
        }

        private fun idle(session: FocusSession): FocusSession =
            session.copy(
                phase = FocusPhase.IDLE,
                segmentStartMillis = null,
                segmentLengthMillis = 0L,
                pausedTotalMillis = 0L,
                pauseStartedAtMillis = null,
                onBreak = false,
            )
    }
}

fun FocusSession.pause(nowMillis: Long): FocusSession {
    if (phase != FocusPhase.WORK && phase != FocusPhase.BREAK) return this
    if (pauseStartedAtMillis != null) return this
    return copy(phase = FocusPhase.PAUSED, pauseStartedAtMillis = nowMillis)
}

fun FocusSession.resume(nowMillis: Long): FocusSession {
    if (phase != FocusPhase.PAUSED) return this
    val pausedFor = nowMillis - (pauseStartedAtMillis ?: return this)
    return copy(
        phase = if (onBreak) FocusPhase.BREAK else FocusPhase.WORK,
        pausedTotalMillis = pausedTotalMillis + pausedFor.coerceAtLeast(0L),
        pauseStartedAtMillis = null,
    )
}
