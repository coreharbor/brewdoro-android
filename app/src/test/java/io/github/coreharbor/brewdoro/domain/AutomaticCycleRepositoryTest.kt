// Copyright (c) 2026 Brewdoro contributors. SPDX-License-Identifier: MIT
package io.github.coreharbor.brewdoro.domain

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class AutomaticCycleRepositoryTest {
    private val events = mutableListOf<String>()
    private var reading = ClockReading(1_000_000, 100_000, "a")
    private val clock = TimerClock { reading }
    private class Store(initial: TimerSnapshot, private val events: MutableList<String>) : SnapshotStore {
        var snapshot = initial
        var fail = false
        var writes = 0
        override fun load() = snapshot
        override fun save(snapshot: TimerSnapshot) {
            if (fail) throw IOException("disk full")
            this.snapshot = SnapshotCodec.decode(SnapshotCodec.encode(snapshot))!!
            writes++
            events.add("save")
        }
    }
    private inner class Effects : TimerEffects {
        var scheduled: TimerSnapshot? = null
        val completions = mutableListOf<TimerMode>()
        var dismissals = 0
        override fun synchronize(snapshot: TimerSnapshot, force: Boolean) {
            assertEquals(store.snapshot, snapshot)
            scheduled = snapshot
            events.add("synchronize")
        }
        override fun completed(mode: TimerMode) {
            // These assertions also prove a running/idle successor, not a transient
            // FINISHED snapshot, was committed and scheduled before each alert.
            assertEquals(store.snapshot, scheduled)
            completions.add(mode)
            events.add("completed")
        }
        override fun dismissCompletion() { dismissals++; events.add("dismiss") }
    }
    private val store = Store(TimerSnapshot(settings = TimerSettings(1, 2, 3)), events)
    private val effects = Effects()
    private val repo = TimerRepository(store, clock, effects)
    private fun advance(ms: Long) { reading = reading.copy(wallMs = reading.wallMs + ms, elapsedMs = reading.elapsedMs + ms) }

    @Test fun automaticCompletionCommitsSchedulesThenNotifiesAndLeavesAlertAlone() {
        val old = repo.start().snapshot
        events.clear()
        advance(old.durationMs)
        val next = repo.alarm(old.generation).snapshot
        assertEquals(listOf("save", "synchronize", "completed"), events)
        assertEquals(TimerMode.SHORT_BREAK, next.mode)
        assertEquals(TimerStatus.RUNNING, next.status)
        assertEquals(120_000L, repo.peek().remainingMs)
        val dismissals = effects.dismissals
        val writes = store.writes
        repeat(100) { advance(100); repo.refresh() }
        repo.refresh(true)
        repo.alarm(old.generation)
        assertEquals(dismissals, effects.dismissals)
        assertEquals(writes, store.writes)
        assertEquals(listOf(TimerMode.FOCUS), effects.completions)
        assertEquals(next.generation, repo.peek().snapshot.generation)
    }
    @Test fun eachNewPhaseAlertsExactlyOnceWithoutAnyManualStart() {
        repo.start()
        val expected = listOf(TimerMode.FOCUS, TimerMode.SHORT_BREAK,
            TimerMode.FOCUS, TimerMode.SHORT_BREAK, TimerMode.FOCUS, TimerMode.SHORT_BREAK,
            TimerMode.FOCUS, TimerMode.LONG_BREAK)
        for (mode in expected) {
            val s = repo.peek().snapshot
            assertEquals(mode, s.mode)
            advance(s.durationMs)
            repo.alarm(s.generation)
            repo.alarm(s.generation)
            repo.refresh()
        }
        assertEquals(expected, effects.completions)
        assertEquals(TimerMode.FOCUS, repo.peek().snapshot.mode)
        assertEquals(0, repo.peek().snapshot.completedFocus)
        assertEquals(TimerStatus.RUNNING, store.snapshot.status)
        assertEquals(9, store.writes) // One start plus eight atomic completions.
        assertEquals(1, effects.dismissals) // Initial user start only.
    }
    @Test fun concurrentRefreshAndReceiverDeliveriesCountNotifyAndPersistOnlyOnce() {
        val s = repo.start().snapshot
        advance(s.durationMs)
        events.clear()
        val writes = store.writes
        val gate = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(8)
        try {
            val tasks = (0 until 64).map { i -> pool.submit {
                gate.await()
                if (i % 2 == 0) repo.refresh() else repo.alarm(s.generation)
            } }
            gate.countDown()
            tasks.forEach { it.get(5, TimeUnit.SECONDS) }
        } finally { pool.shutdownNow() }
        assertEquals(1, repo.peek().snapshot.completedFocus)
        assertEquals(TimerStatus.RUNNING, repo.peek().snapshot.status)
        assertEquals(TimerMode.SHORT_BREAK, repo.peek().snapshot.mode)
        assertEquals(listOf(TimerMode.FOCUS), effects.completions)
        assertEquals(writes + 1, store.writes)
        assertEquals(1, events.count { it == "completed" })
    }
    @Test fun phaseBoundPauseAndAlarmRaceCannotPauseSuccessor() {
        val s = repo.start().snapshot
        advance(s.durationMs)
        val gate = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val pause = pool.submit { gate.await(); repo.pause(s.generation) }
            val alarm = pool.submit { gate.await(); repo.alarm(s.generation) }
            gate.countDown()
            pause.get(5, TimeUnit.SECONDS)
            alarm.get(5, TimeUnit.SECONDS)
        } finally { pool.shutdownNow() }
        assertEquals(TimerStatus.RUNNING, repo.peek().snapshot.status)
        assertEquals(TimerMode.SHORT_BREAK, repo.peek().snapshot.mode)
        assertEquals(1, effects.completions.size)
    }
    @Test fun stalePauseAfterReceiverCompletionHasNoEffects() {
        val s = repo.start().snapshot
        advance(s.durationMs)
        val next = repo.alarm(s.generation).snapshot
        events.clear()
        assertEquals(next, repo.pause(s.generation).snapshot)
        assertTrue(events.isEmpty())
        assertEquals(TimerStatus.PAUSED, repo.pause(next.generation).snapshot.status)
    }
    @Test fun failedAtomicSuccessorWriteDoesNotScheduleCountDismissOrNotifyThenRetriesOnce() {
        val s = repo.start().snapshot
        advance(s.durationMs + 90_000_000)
        events.clear()
        store.fail = true
        try { repo.alarm(s.generation); fail("Expected disk failure") } catch (_: IOException) { }
        assertTrue(events.isEmpty())
        assertEquals(s, store.snapshot)
        assertEquals(s, repo.peek().snapshot)
        assertTrue(effects.completions.isEmpty())
        store.fail = false
        val next = repo.alarm(s.generation).snapshot
        assertEquals(listOf("save", "synchronize", "completed"), events)
        assertEquals(1, next.completedFocus)
        assertEquals(120_000L, repo.peek().remainingMs)
        assertEquals(reading.elapsedMs + 120_000, next.deadlineElapsedMs)
        repo.alarm(s.generation)
        repo.refresh()
        assertEquals(1, effects.completions.size)
    }
    @Test fun reopenSuccessorDoesNotNotifyAgainOrWithdrawExistingAlert() {
        val s = repo.start().snapshot
        advance(s.durationMs)
        val next = repo.alarm(s.generation).snapshot
        advance(12_345)
        val restoredEffects = Effects()
        val restored = TimerRepository(store, clock, restoredEffects)
        events.clear()
        val view = restored.refresh(true)
        restored.alarm(s.generation)
        assertEquals(next, view.snapshot)
        assertEquals(107_655L, view.remainingMs)
        assertEquals(listOf("synchronize"), events)
        assertTrue(restoredEffects.completions.isEmpty())
        assertEquals(0, restoredEffects.dismissals)
    }
    @Test fun overdueRestoreStartsOneFullSuccessorAtRestoreTime() {
        val s = repo.start().snapshot
        advance(90_000_000)
        val restoredEffects = Effects()
        val restored = TimerRepository(store, clock, restoredEffects)
        val next = restored.refresh(true).snapshot
        assertEquals(1, next.completedFocus)
        assertEquals(TimerMode.SHORT_BREAK, next.mode)
        assertEquals(TimerStatus.RUNNING, next.status)
        assertEquals(reading.elapsedMs + 120_000, next.deadlineElapsedMs)
        assertEquals(120_000L, restored.peek().remainingMs)
        assertEquals(listOf(TimerMode.FOCUS), restoredEffects.completions)
        assertNotEquals(s.generation, next.generation)
    }
    @Test fun oldDeliveryCannotReconcileAnOverdueSuccessorOrRescheduleAnything() {
        val s = repo.start().snapshot
        advance(s.durationMs)
        val next = repo.alarm(s.generation).snapshot
        advance(next.durationMs + 99_000)
        events.clear()
        assertEquals(next, repo.alarm(s.generation).snapshot)
        assertTrue(events.isEmpty())
        assertEquals(1, effects.completions.size)
        assertEquals(TimerMode.FOCUS, repo.alarm(next.generation).snapshot.mode)
        assertEquals(2, effects.completions.size)
    }
    @Test fun resetAfterCompletionCancelsAlarmAndWithdrawsAlertWithoutRestart() {
        val s = repo.start().snapshot
        advance(s.durationMs)
        val next = repo.alarm(s.generation).snapshot
        val reset = repo.resetTimer().snapshot
        assertEquals(TimerStatus.IDLE, effects.scheduled!!.status)
        assertEquals(TimerStatus.IDLE, reset.status)
        assertEquals(2, effects.dismissals)
        advance(90_000_000)
        repo.alarm(s.generation)
        repo.alarm(next.generation)
        repo.refresh(true)
        assertEquals(reset, repo.peek().snapshot)
        assertEquals(1, effects.completions.size)
    }
    @Test fun resetExactlyAtExpiryCommitsCountButStopsChainAndLeavesNoAlert() {
        val s = repo.start().snapshot
        advance(s.durationMs)
        events.clear()
        val reset = repo.resetTimer().snapshot
        assertEquals(1, reset.completedFocus)
        assertEquals(TimerStatus.IDLE, reset.status)
        assertEquals(listOf("save", "synchronize", "dismiss"), events)
        assertTrue(effects.completions.isEmpty())
        advance(90_000_000)
        repo.alarm(s.generation)
        repo.refresh(true)
        assertEquals(reset, repo.peek().snapshot)
        assertTrue(effects.completions.isEmpty())
    }
    @Test fun manualCycleIdleSuccessorStillNotifiesThenStartDismisses() {
        repo.updateSettings(store.snapshot.settings.copy(autoStartEnabled = false))
        val s = repo.start().snapshot
        advance(s.durationMs)
        events.clear()
        val next = repo.alarm(s.generation).snapshot
        assertEquals(TimerStatus.IDLE, next.status)
        assertEquals(TimerMode.SHORT_BREAK, next.mode)
        assertEquals(120_000L, next.remainingMs)
        assertEquals(listOf("save", "synchronize", "completed"), events)
        events.clear()
        assertEquals(TimerMode.SHORT_BREAK, repo.start().snapshot.mode)
        assertEquals(listOf("save", "synchronize", "dismiss"), events)
    }
    @Test fun disabledCycleFinishesAndNotifiesInPlaceThenManualStartRestartsSameMode() {
        repo.updateSettings(store.snapshot.settings.copy(cycleEnabled = false))
        val s = repo.start().snapshot
        advance(90_000_000)
        val finished = repo.alarm(s.generation).snapshot
        assertEquals(TimerStatus.FINISHED, finished.status)
        assertEquals(TimerMode.FOCUS, finished.mode)
        assertEquals(0, finished.completedFocus)
        assertEquals(listOf(TimerMode.FOCUS), effects.completions)
        repo.refresh(true)
        val restarted = repo.start().snapshot
        assertEquals(TimerMode.FOCUS, restarted.mode)
        assertEquals(60_000L, restarted.remainingMs)
        assertEquals(1, effects.completions.size)
    }
}
