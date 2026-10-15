package com.zendolauncher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusSessionTest {

    @Test
    fun `remaining counts down from timestamps not ticks`() {
        val s = FocusSession.startWork(25, 5, nowMillis = 0L)
        assertEquals(25 * 60_000L, FocusSession.remaining(s, 0L))
        assertEquals(25 * 60_000L - 60_000L, FocusSession.remaining(s, 60_000L))
    }

    @Test
    fun `pause freezes time and resume spends from the same segment`() {
        var s = FocusSession.startWork(25, 5, nowMillis = 0L)
        s = s.pause(60_000L)
        assertEquals(FocusPhase.PAUSED, s.phase)
        // Ten minutes pass while paused: the remainder does not move.
        assertEquals(25 * 60_000L - 60_000L, FocusSession.remaining(s, 660_000L))
        s = s.resume(660_000L)
        assertEquals(FocusPhase.WORK, s.phase)
        assertEquals(25 * 60_000L - 60_000L, FocusSession.remaining(s, 660_000L))
    }

    @Test
    fun `pause outside a segment and double pause are no-ops`() {
        val idle = FocusSession()
        assertEquals(idle, idle.pause(0L))
        val s = FocusSession.startWork(25, 5, nowMillis = 0L).pause(10_000L)
        assertEquals(s, s.pause(20_000L))
    }

    @Test
    fun `recovery records an ended segment exactly once and resumes a live one`() {
        val ended = FocusSession.startWork(25, 5, nowMillis = 0L)
        val (session, history) = FocusSession.recover(ended, emptyList(), 30 * 60_000L)
        assertEquals(FocusPhase.IDLE, session.phase)
        assertEquals(1, history.size)
        assertTrue(history.single().finishedNaturally)
        // Recovering the result again must not duplicate the record.
        val (session2, history2) = FocusSession.recover(session, history, 60 * 60_000L)
        assertEquals(1, history2.size)
        assertEquals(FocusPhase.IDLE, session2.phase)

        val live = FocusSession.startWork(25, 5, nowMillis = 0L)
        val (liveSession, liveHistory) = FocusSession.recover(live, emptyList(), 60_000L)
        assertEquals(FocusPhase.WORK, liveSession.phase)
        assertTrue(liveHistory.isEmpty())
    }

    @Test
    fun `a paused segment survives recovery frozen`() {
        val s = FocusSession.startWork(25, 5, nowMillis = 0L).pause(60_000L)
        // Hours later the pause is still exactly where it was left.
        val (session, history) = FocusSession.recover(s, emptyList(), 5 * 3_600_000L)
        assertEquals(FocusPhase.PAUSED, session.phase)
        assertTrue(history.isEmpty())
        assertEquals(25 * 60_000L - 60_000L, FocusSession.remaining(session, 5 * 3_600_000L))
    }
}
