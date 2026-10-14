// Copyright (c) 2026 Brewdoro contributors. SPDX-License-Identifier: MIT
package io.github.coreharbor.brewdoro.domain

import org.junit.Assert.*
import org.junit.Test

class TimerEngineTest {
    private var serial = 0
    private val engine = TimerEngine { "generation-" + ++serial }
    private var now = ClockReading(1_000_000, 100_000, "boot-a")
    private val short = TimerSettings(1, 1, 1)
    private fun idle(count: Int = 0) = TimerSnapshot(settings = short, completedFocus = count)
    private fun running(count: Int = 0) = engine.start(idle(count), now).snapshot
    private fun advance(ms: Long) { now = now.copy(wallMs = now.wallMs + ms, elapsedMs = now.elapsedMs + ms) }
    private fun complete(s: TimerSnapshot): TimerSnapshot {
        advance(s.remainingMs)
        return engine.reconcile(s, now).snapshot
    }

    @Test fun defaultsAreTwentyFiveFiveFifteen() {
        val settings = TimerSettings()
        assertEquals(1_500_000L, settings.durationMs(TimerMode.FOCUS))
        assertEquals(300_000L, settings.durationMs(TimerMode.SHORT_BREAK))
        assertEquals(900_000L, settings.durationMs(TimerMode.LONG_BREAK))
    }
    @Test fun elapsedDeadlineNotTicksIsAuthoritative() {
        val s = running()
        advance(24_321)
        assertEquals(35_679L, engine.remaining(s, now))
        assertEquals(s, engine.reconcile(s, now).snapshot)
    }
    @Test fun repeatedStartDoesNotExtendDeadline() {
        val s = running()
        advance(10_000)
        assertEquals(s, engine.start(s, now).snapshot)
    }
    @Test fun wallForwardChangeWithinBootDoesNotFinish() {
        val s = running()
        now = now.copy(wallMs = now.wallMs + 86_400_000)
        val r = engine.reconcile(s, now)
        assertNull(r.completedMode)
        assertEquals(60_000L, engine.remaining(r.snapshot, now))
        assertEquals(s.deadlineElapsedMs, r.snapshot.deadlineElapsedMs)
        assertEquals(now.wallMs + 60_000, r.snapshot.deadlineWallMs)
    }
    @Test fun wallBackwardWithinBootDoesNotExtend() {
        val s = running()
        advance(15_000)
        now = now.copy(wallMs = 50_000)
        val r = engine.reconcile(s, now).snapshot
        assertEquals(45_000L, engine.remaining(r, now))
        advance(45_000)
        assertEquals(TimerStatus.FINISHED, engine.reconcile(r, now).snapshot.status)
    }
    @Test fun delayedCallbackCompletesOnlyOneSession() {
        val s = running()
        advance(900_000)
        val r = engine.reconcile(s, now)
        assertEquals(TimerMode.FOCUS, r.completedMode)
        assertEquals(1, r.snapshot.completedFocus)
        assertEquals(TimerStatus.FINISHED, r.snapshot.status)
        assertEquals(TimerMode.SHORT_BREAK, r.snapshot.nextMode)
        assertEquals(0L, engine.remaining(r.snapshot, now))
    }
    @Test fun completingAgainIsNoOp() {
        val finished = complete(running())
        repeat(10) {
            val r = engine.reconcile(finished, now)
            assertEquals(finished, r.snapshot)
            assertNull(r.completedMode)
        }
    }
    @Test fun earlyAlarmDoesNotFinish() {
        val s = running()
        advance(59_999)
        assertNull(engine.alarm(s, s.generation, now).completedMode)
        assertEquals(1L, engine.remaining(s, now))
    }
    @Test fun expiryIsInclusive() {
        val s = running()
        advance(60_000)
        assertEquals(TimerStatus.FINISHED, engine.alarm(s, s.generation, now).snapshot.status)
    }
    @Test fun staleAlarmCannotCompleteOverdueNewGeneration() {
        val old = running()
        val newer = engine.start(engine.resetTimer(old, now).snapshot, now).snapshot
        advance(70_000)
        assertEquals(newer, engine.alarm(newer, old.generation, now).snapshot)
        assertNull(engine.alarm(newer, old.generation, now).completedMode)
        assertEquals(1, engine.reconcile(newer, now).snapshot.completedFocus)
    }
    @Test fun pausedTimersIgnoreAlarms() {
        val s = running()
        val paused = engine.pause(s, now).snapshot
        advance(80_000)
        assertEquals(paused, engine.alarm(paused, s.generation, now).snapshot)
    }
    @Test fun pauseResumeRetainsExactRemainingAndChangesGeneration() {
        val s = running()
        advance(17_123)
        val paused = engine.pause(s, now).snapshot
        assertEquals(42_877L, paused.remainingMs)
        assertNotEquals(s.generation, paused.generation)
        advance(5_000_000)
        val resumed = engine.start(paused, now).snapshot
        assertEquals(42_877L, engine.remaining(resumed, now))
        assertNotEquals(paused.generation, resumed.generation)
        advance(42_877)
        assertEquals(1, engine.reconcile(resumed, now).snapshot.completedFocus)
    }
    @Test fun pauseExactlyAtExpiryFinishesInsteadOfFreezingZero() {
        val s = running()
        advance(60_000)
        val r = engine.pause(s, now)
        assertEquals(TimerStatus.FINISHED, r.snapshot.status)
        assertEquals(1, r.snapshot.completedFocus)
        assertEquals(TimerMode.FOCUS, r.completedMode)
    }
    @Test fun pauseOneMillisecondBeforeExpiryIsNotCompletion() {
        val s = running()
        advance(59_999)
        val paused = engine.pause(s, now).snapshot
        advance(100_000)
        assertEquals(TimerStatus.PAUSED, engine.reconcile(paused, now).snapshot.status)
        assertEquals(1L, paused.remainingMs)
        assertEquals(0, paused.completedFocus)
    }
    @Test fun resetTimerPreservesCycleAndMode() {
        val s = running(3)
        val reset = engine.resetTimer(s, now).snapshot
        assertEquals(3, reset.completedFocus)
        assertEquals(s.mode, reset.mode)
        assertEquals(TimerStatus.IDLE, reset.status)
        assertEquals(60_000L, reset.remainingMs)
        assertNotEquals(s.generation, reset.generation)
    }
    @Test fun resetAtExpiryCountsCompletionOnlyOnce() {
        val s = running(2)
        advance(60_000)
        val reset = engine.resetTimer(s, now).snapshot
        assertEquals(3, reset.completedFocus)
        assertEquals(3, engine.alarm(reset, s.generation, now).snapshot.completedFocus)
    }
    @Test fun resetCycleResetsCountAndFocusEvenIfPausedInBreak() {
        var s = engine.selectMode(idle(3), TimerMode.SHORT_BREAK, now).snapshot
        s = engine.pause(engine.start(s, now).snapshot, now).snapshot
        val reset = engine.resetCycle(s, now).snapshot
        assertEquals(0, reset.completedFocus)
        assertEquals(TimerMode.FOCUS, reset.mode)
        assertEquals(TimerStatus.IDLE, reset.status)
    }
    @Test fun modeChangesBlockedWhileRunningAndPaused() {
        val s = running()
        assertEquals(s, engine.selectMode(s, TimerMode.LONG_BREAK, now).snapshot)
        val paused = engine.pause(s, now).snapshot
        assertEquals(paused, engine.selectMode(paused, TimerMode.SHORT_BREAK, now).snapshot)
    }
    @Test fun idleModeChangeUsesConfiguredDuration() {
        val s = TimerSnapshot(settings = TimerSettings(10, 3, 20), completedFocus = 2)
        val changed = engine.selectMode(s, TimerMode.LONG_BREAK, now).snapshot
        assertEquals(1_200_000L, changed.durationMs)
        assertEquals(2, changed.completedFocus)
    }
    @Test fun settingsDoNotMutateRunningSession() {
        val s = running()
        advance(5_000)
        val changed = engine.settings(s, TimerSettings(50, 10, 30), now).snapshot
        assertEquals(s.durationMs, changed.durationMs)
        assertEquals(s.deadlineElapsedMs, changed.deadlineElapsedMs)
        assertEquals(s.generation, changed.generation)
        assertEquals(55_000L, engine.remaining(changed, now))
    }
    @Test fun settingsDoNotMutatePausedSessionButApplyOnReset() {
        val paused = engine.pause(running(), now).snapshot
        val changed = engine.settings(paused, TimerSettings(50, 10, 30), now).snapshot
        assertEquals(60_000L, changed.durationMs)
        assertEquals(60_000L, engine.start(changed, now).snapshot.remainingMs)
        assertEquals(3_000_000L, engine.resetTimer(changed, now).snapshot.durationMs)
    }
    @Test fun idleSettingsApplyImmediately() {
        val changed = engine.settings(idle(), TimerSettings(2, 3, 4), now).snapshot
        assertEquals(120_000L, changed.remainingMs)
    }
    @Test fun fourthFocusOffersLongBreakWithoutStarting() {
        val completed = complete(running(3))
        assertEquals(4, completed.completedFocus)
        assertEquals(TimerMode.LONG_BREAK, completed.nextMode)
        assertEquals(TimerStatus.FINISHED, completed.status)
        val next = engine.start(completed, now).snapshot
        assertEquals(TimerMode.LONG_BREAK, next.mode)
        assertEquals(TimerStatus.RUNNING, next.status)
        val afterBreak = complete(next)
        assertEquals(0, afterBreak.completedFocus)
        assertEquals(TimerMode.FOCUS, afterBreak.nextMode)
    }
    @Test fun fourFocusFullCycleWithExplicitBreaks() {
        var s = idle()
        repeat(4) { i ->
            s = complete(engine.start(s, now).snapshot)
            assertEquals(i + 1, s.completedFocus)
            assertEquals(if (i == 3) TimerMode.LONG_BREAK else TimerMode.SHORT_BREAK, s.nextMode)
            s = complete(engine.start(s, now).snapshot)
            assertEquals(if (i == 3) 0 else i + 1, s.completedFocus)
            assertEquals(TimerMode.FOCUS, s.nextMode)
        }
    }
    @Test fun rebootRebasesUsingWallAndInvalidatesOldAlarm() {
        val s = running()
        now = now.copy(wallMs = now.wallMs + 20_000, elapsedMs = 2_000, bootId = "boot-b")
        val r = engine.reconcile(s, now).snapshot
        assertEquals(40_000L, r.remainingMs)
        assertEquals(42_000L, r.deadlineElapsedMs)
        assertEquals("boot-b", r.bootId)
        assertNotEquals(s.generation, r.generation)
        now = now.copy(wallMs = now.wallMs + 900_000)
        assertEquals(40_000L, engine.remaining(r, now))
    }
    @Test fun rebootAfterExpiryCompletesOnce() {
        val s = running(3)
        now = now.copy(wallMs = now.wallMs + 70_000, elapsedMs = 100, bootId = "boot-b")
        val r = engine.reconcile(s, now)
        assertEquals(TimerStatus.FINISHED, r.snapshot.status)
        assertEquals(4, r.snapshot.completedFocus)
        assertNull(engine.reconcile(r.snapshot, now).completedMode)
    }
    @Test fun rebootWithBackwardWallIsBoundedBySavedRemainder() {
        val s = running()
        now = now.copy(wallMs = 1, elapsedMs = 1, bootId = "boot-b")
        val r = engine.reconcile(s, now).snapshot
        assertEquals(60_000L, r.remainingMs)
        advance(60_000)
        assertEquals(TimerStatus.FINISHED, engine.reconcile(r, now).snapshot.status)
    }
    @Test fun monotonicRegressionFallsBackEvenWithIncorrectBootId() {
        val s = running()
        now = now.copy(wallMs = now.wallMs + 30_000, elapsedMs = 5)
        val r = engine.reconcile(s, now).snapshot
        assertEquals(30_000L, r.remainingMs)
        assertNotEquals(s.generation, r.generation)
    }
    @Test fun unknownBootNeverBlindlyTrustsMonotonicDeadline() {
        val s = running()
        now = now.copy(wallMs = now.wallMs + 70_000, elapsedMs = 0, bootId = null)
        assertEquals(TimerStatus.FINISHED, engine.reconcile(s, now).snapshot.status)
    }
    @Test fun restorationAfterWallCorrectionUsesUpdatedFallback() {
        val s = running()
        advance(10_000)
        now = now.copy(wallMs = now.wallMs + 5_000_000)
        val anchored = engine.reconcile(s, now).snapshot
        now = now.copy(wallMs = now.wallMs + 15_000, elapsedMs = 1, bootId = "boot-b")
        assertEquals(35_000L, engine.reconcile(anchored, now).snapshot.remainingMs)
    }
    @Test fun pausedRestorationIgnoresRebootAndWallTime() {
        advance(10_000)
        val s = engine.pause(running(), now).snapshot
        now = ClockReading(9_000_000, 1, "boot-b")
        assertEquals(s, engine.reconcile(s, now).snapshot)
    }
    @Test(expected = IllegalArgumentException::class) fun zeroDurationRejected() { TimerSettings(0, 5, 15) }
    @Test(expected = IllegalArgumentException::class) fun excessiveDurationRejected() { TimerSettings(25, 181, 15) }
}
