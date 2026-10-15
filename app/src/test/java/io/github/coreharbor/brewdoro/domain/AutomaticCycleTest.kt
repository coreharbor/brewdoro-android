// Copyright (c) 2026 Brewdoro contributors. SPDX-License-Identifier: MIT
package io.github.coreharbor.brewdoro.domain

import org.junit.Assert.*
import org.junit.Test

class AutomaticCycleTest {
    private var serial = 0
    private val engine = TimerEngine { "auto-${++serial}" }
    private var now = ClockReading(1_000_000, 100_000, "a")
    private val settings = TimerSettings(1, 2, 3)
    private fun running(settings: TimerSettings = this.settings, count: Int = 0) =
        engine.start(TimerSnapshot(settings = settings, completedFocus = count), now).snapshot
    private fun advance(ms: Long) { now = now.copy(wallMs = now.wallMs + ms, elapsedMs = now.elapsedMs + ms) }
    private fun finish(s: TimerSnapshot): Transition {
        advance(engine.remaining(s, now))
        return engine.alarm(s, s.generation, now)
    }
    private fun assertFresh(predecessor: TimerSnapshot, successor: TimerSnapshot, mode: TimerMode) {
        assertEquals(mode, successor.mode)
        assertEquals(TimerStatus.RUNNING, successor.status)
        assertEquals(settings.durationMs(mode), successor.durationMs)
        assertEquals(successor.durationMs, engine.remaining(successor, now))
        assertEquals(now.elapsedMs + successor.durationMs, successor.deadlineElapsedMs)
        assertEquals(now.wallMs + successor.durationMs, successor.deadlineWallMs)
        assertEquals(now.bootId, successor.bootId)
        assertNotEquals(predecessor.generation, successor.generation)
        assertNull(successor.nextMode)
    }

    @Test fun androidDefaultsEnableCycleAndAutoStart() {
        assertTrue(TimerSettings().cycleEnabled)
        assertTrue(TimerSettings().autoStartEnabled)
    }
    @Test fun oneStartRunsFourFocusThreeShortOneLongThenFreshCycle() {
        var s = running()
        repeat(8) { index ->
            val before = s
            val result = finish(s)
            val mode = when {
                index == 6 -> TimerMode.LONG_BREAK
                index % 2 == 0 -> TimerMode.SHORT_BREAK
                else -> TimerMode.FOCUS
            }
            s = result.snapshot
            assertEquals(before.mode, result.completedMode)
            assertFresh(before, s, mode)
            assertEquals(if (index == 7) 0 else index / 2 + 1, s.completedFocus)
        }
        assertEquals(TimerMode.FOCUS, s.mode)
        assertEquals(0, s.completedFocus)
        val result = finish(s)
        assertFresh(s, result.snapshot, TimerMode.SHORT_BREAK)
        assertEquals(1, result.snapshot.completedFocus)
    }
    @Test fun delayedAlarmStartsExactlyOneFullSuccessorAtObservedNow() {
        val s = running()
        advance(10 * 86_400_000L)
        val result = engine.alarm(s, s.generation, now)
        assertFresh(s, result.snapshot, TimerMode.SHORT_BREAK)
        assertEquals(1, result.snapshot.completedFocus)
        repeat(20) {
            assertEquals(result.snapshot, engine.reconcile(result.snapshot, now).snapshot)
            assertNull(engine.reconcile(result.snapshot, now).completedMode)
        }
    }
    @Test fun stalePredecessorAlarmCannotCompleteEvenOverdueSuccessor() {
        val s = running()
        val successor = finish(s).snapshot
        advance(successor.durationMs + 99_000)
        val stale = engine.alarm(successor, s.generation, now)
        assertEquals(successor, stale.snapshot)
        assertNull(stale.completedMode)
        val actual = engine.alarm(successor, successor.generation, now)
        assertFresh(successor, actual.snapshot, TimerMode.FOCUS)
    }
    @Test fun pauseAtExpiryLeavesAutoStartedSuccessorRunning() {
        val s = running()
        advance(s.durationMs)
        val result = engine.pause(s, now)
        assertEquals(TimerMode.FOCUS, result.completedMode)
        assertFresh(s, result.snapshot, TimerMode.SHORT_BREAK)
    }
    @Test fun startAtExpiryDoesNotOverrideAutoStartOff() {
        val s = running(settings.copy(autoStartEnabled = false))
        advance(s.durationMs)
        val selected = engine.start(s, now)
        assertEquals(TimerMode.FOCUS, selected.completedMode)
        assertEquals(TimerStatus.IDLE, selected.snapshot.status)
        assertEquals(TimerMode.SHORT_BREAK, selected.snapshot.mode)
    }
    @Test fun manualCycleSelectsFullIdleNextModeAndOneStartIsEnough() {
        var s = running(settings.copy(autoStartEnabled = false), count = 3)
        val selected = finish(s).snapshot
        assertEquals(TimerStatus.IDLE, selected.status)
        assertEquals(TimerMode.LONG_BREAK, selected.mode)
        assertEquals(180_000L, selected.remainingMs)
        assertEquals(4, selected.completedFocus)
        assertNull(selected.nextMode)
        advance(90_000_000)
        assertEquals(selected, engine.reconcile(selected, now).snapshot)
        s = engine.start(selected, now).snapshot
        assertEquals(TimerMode.LONG_BREAK, s.mode)
        assertEquals(TimerStatus.RUNNING, s.status)
        val focus = finish(s).snapshot
        assertEquals(TimerStatus.IDLE, focus.status)
        assertEquals(TimerMode.FOCUS, focus.mode)
        assertEquals(0, focus.completedFocus)
    }
    @Test fun cycleOffNeverAdvancesStartsOrCountsRegardlessOfAutoStart() {
        for (auto in listOf(false, true)) for (mode in TimerMode.values()) {
            val configured = settings.copy(cycleEnabled = false, autoStartEnabled = auto)
            val idle = TimerSnapshot(settings = configured, mode = mode, completedFocus = 2)
            val s = engine.start(idle, now).snapshot
            val result = finish(s)
            assertEquals(mode, result.completedMode)
            assertEquals(mode, result.snapshot.mode)
            assertEquals(mode, result.snapshot.nextMode)
            assertEquals(TimerStatus.FINISHED, result.snapshot.status)
            assertEquals(0L, result.snapshot.remainingMs)
            assertEquals(2, result.snapshot.completedFocus)
            assertNull(engine.reconcile(result.snapshot, now).completedMode)
            val restarted = engine.start(result.snapshot, now).snapshot
            assertEquals(mode, restarted.mode)
            assertEquals(configured.durationMs(mode), restarted.remainingMs)
        }
    }
    @Test fun disablingCycleResetsCountAndKeepsActiveDurationThenFinishesInPlace() {
        val s = running(count = 3)
        advance(10_000)
        val disabled = engine.settings(s, TimerSettings(5, 6, 7, false, true), now).snapshot
        assertEquals(0, disabled.completedFocus)
        assertEquals(s.generation, disabled.generation)
        assertEquals(s.durationMs, disabled.durationMs)
        assertEquals(s.deadlineElapsedMs, disabled.deadlineElapsedMs)
        advance(50_000)
        val result = engine.reconcile(disabled, now).snapshot
        assertEquals(TimerStatus.FINISHED, result.status)
        assertEquals(TimerMode.FOCUS, result.mode)
        assertEquals(0, result.completedFocus)
    }
    @Test fun disablingExactlyAtExpiryCannotLaunchOneLastSuccessor() {
        val s = running(count = 3)
        advance(s.durationMs)
        val disabled = engine.settings(s, settings.copy(cycleEnabled = false), now)
        assertEquals(TimerMode.FOCUS, disabled.completedMode)
        assertEquals(TimerStatus.FINISHED, disabled.snapshot.status)
        assertEquals(TimerMode.FOCUS, disabled.snapshot.mode)
        assertEquals(0, disabled.snapshot.completedFocus)
    }
    @Test fun disablingAutoStartExactlyAtExpirySelectsIdleSuccessor() {
        val s = running()
        advance(s.durationMs)
        val result = engine.settings(s, settings.copy(autoStartEnabled = false), now)
        assertEquals(TimerStatus.IDLE, result.snapshot.status)
        assertEquals(TimerMode.SHORT_BREAK, result.snapshot.mode)
        assertEquals(120_000L, result.snapshot.remainingMs)
    }
    @Test fun resetAtExpiryAndStaleDeliveryCannotContinueChain() {
        val s = running()
        advance(s.durationMs)
        val reset = engine.resetTimer(s, now).snapshot
        assertEquals(TimerStatus.IDLE, reset.status)
        assertEquals(1, reset.completedFocus)
        advance(86_400_000)
        assertEquals(reset, engine.alarm(reset, s.generation, now).snapshot)
        assertEquals(reset, engine.reconcile(reset, now).snapshot)
        val resetCycle = engine.resetCycle(s, now).snapshot
        assertEquals(TimerMode.FOCUS, resetCycle.mode)
        assertEquals(TimerStatus.IDLE, resetCycle.status)
        assertEquals(0, resetCycle.completedFocus)
    }
    @Test fun rebootAfterLongDelayStartsOneFullSuccessorOnNewClock() {
        val s = running(count = 3)
        now = ClockReading(now.wallMs + 86_400_000, 100, "b")
        val result = engine.reconcile(s, now)
        assertFresh(s, result.snapshot, TimerMode.LONG_BREAK)
        assertEquals(4, result.snapshot.completedFocus)
    }
    @Test fun autoSuccessorStillUsesMonotonicClockAndRebasesAfterReboot() {
        val successor = finish(running()).snapshot
        advance(10_000)
        now = now.copy(wallMs = now.wallMs + 500_000)
        val anchored = engine.reconcile(successor, now).snapshot
        assertEquals(110_000L, engine.remaining(anchored, now))
        now = ClockReading(now.wallMs + 20_000, 10, "b")
        val rebased = engine.reconcile(anchored, now).snapshot
        assertEquals(90_000L, rebased.remainingMs)
        assertNotEquals(anchored.generation, rebased.generation)
        assertEquals(90_010L, rebased.deadlineElapsedMs)
    }
    @Test fun legacyFinishedRemainsInertUntilManualStartWithoutCountingAgain() {
        val legacy = TimerSnapshot(settings = settings, status = TimerStatus.FINISHED,
            completedFocus = 4, nextMode = TimerMode.LONG_BREAK, remainingMs = 0)
        assertEquals(legacy, engine.reconcile(legacy, now).snapshot)
        assertNull(engine.reconcile(legacy, now).completedMode)
        val started = engine.start(legacy, now).snapshot
        assertEquals(TimerMode.LONG_BREAK, started.mode)
        assertEquals(4, started.completedFocus)
        val disabled = legacy.copy(settings = settings.copy(cycleEnabled = false))
        assertEquals(TimerMode.FOCUS, engine.start(disabled, now).snapshot.mode)
    }
}
