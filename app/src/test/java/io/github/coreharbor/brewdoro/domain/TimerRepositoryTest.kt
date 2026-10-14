// Copyright (c) 2026 Brewdoro contributors. SPDX-License-Identifier: MIT
package io.github.coreharbor.brewdoro.domain

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class TimerRepositoryTest {
    private class MemoryStore(initial: TimerSnapshot? = TimerSnapshot(settings = TimerSettings(1, 1, 1))) : SnapshotStore {
        var saved = initial
        var writes = 0
        var fail = false
        override fun load() = saved
        override fun save(snapshot: TimerSnapshot) {
            if (fail) throw IOException("disk full")
            // Exercise real codec in every repository write, not just in dedicated roundtrip tests.
            saved = SnapshotCodec.decode(SnapshotCodec.encode(snapshot))!!
            writes++
        }
    }
    private class FakeClock : TimerClock {
        @Volatile var reading = ClockReading(1_000_000, 100_000, "a")
        override fun now() = reading
        fun advance(ms: Long) { reading = reading.copy(wallMs = reading.wallMs + ms, elapsedMs = reading.elapsedMs + ms) }
    }
    private class Effects(private val store: MemoryStore) : TimerEffects {
        var completions = 0
        var scheduled: TimerSnapshot? = null
        var forces = 0
        override fun completed(mode: TimerMode) {
            assertEquals(TimerStatus.FINISHED, store.saved!!.status)
            completions++
        }
        override fun synchronize(snapshot: TimerSnapshot, force: Boolean) {
            scheduled = snapshot
            if (force) forces++
        }
    }
    private val store = MemoryStore()
    private val clock = FakeClock()
    private val effects = Effects(store)
    private val repo = TimerRepository(store, clock, effects)

    @Test fun startingCommitsBeforeScheduling() {
        val s = repo.start().snapshot
        assertEquals(s, store.saved)
        assertEquals(s, effects.scheduled)
    }
    @Test fun visibleTicksDoNotWriteEveryFrame() {
        repo.start()
        val writes = store.writes
        repeat(100) { clock.advance(250); repo.refresh() }
        assertEquals(writes, store.writes)
        assertEquals(35_000L, repo.peek().remainingMs)
    }
    @Test fun uiAndAlarmConcurrencyCountsOnce() {
        val generation = repo.start().snapshot.generation
        clock.advance(60_000)
        val pool = Executors.newFixedThreadPool(8)
        val gate = CountDownLatch(1)
        try {
            val tasks = (0 until 32).map { index -> pool.submit {
                gate.await()
                if (index % 2 == 0) repo.refresh() else repo.alarm(generation)
            } }
            gate.countDown()
            tasks.forEach { it.get(5, TimeUnit.SECONDS) }
            assertEquals(1, repo.peek().snapshot.completedFocus)
            assertEquals(1, effects.completions)
            assertEquals(TimerStatus.FINISHED, store.saved!!.status)
        } finally { pool.shutdownNow() }
    }
    @Test fun alarmPauseAtExpiryRaceStillCountsOnce() {
        val generation = repo.start().snapshot.generation
        clock.advance(60_000)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val a = pool.submit { repo.pause() }
            val b = pool.submit { repo.alarm(generation) }
            a.get(5, TimeUnit.SECONDS)
            b.get(5, TimeUnit.SECONDS)
            assertEquals(1, repo.peek().snapshot.completedFocus)
            assertEquals(1, effects.completions)
            assertEquals(TimerStatus.FINISHED, repo.peek().snapshot.status)
        } finally { pool.shutdownNow() }
    }
    @Test fun restartAfterReceiverCompletionDoesNotNotifyAgain() {
        val generation = repo.start().snapshot.generation
        clock.advance(70_000)
        repo.alarm(generation)
        val restoredEffects = Effects(store)
        val restored = TimerRepository(store, clock, restoredEffects)
        restored.refresh(true)
        restored.alarm(generation)
        assertEquals(1, restored.peek().snapshot.completedFocus)
        assertEquals(0, restoredEffects.completions)
    }
    @Test fun restartBeforeExpiryRestoresDeadline() {
        val s = repo.start().snapshot
        clock.advance(25_000)
        val restored = TimerRepository(store, clock, Effects(store))
        val view = restored.refresh(true)
        assertEquals(s.generation, view.snapshot.generation)
        assertEquals(35_000L, view.remainingMs)
    }
    @Test fun restartAfterMissedAlarmCompletesOnRestore() {
        repo.start()
        clock.advance(90_000)
        val restoredEffects = Effects(store)
        val restored = TimerRepository(store, clock, restoredEffects)
        assertEquals(1, restored.refresh(true).snapshot.completedFocus)
        assertEquals(1, restoredEffects.completions)
    }
    @Test fun staleDeliveryAfterResetDoesNotScheduleOrNotify() {
        val old = repo.start().snapshot.generation
        repo.resetTimer()
        val forces = effects.forces
        clock.advance(90_000)
        repo.alarm(old)
        assertEquals(0, effects.completions)
        assertEquals(forces, effects.forces)
        assertEquals(0, repo.peek().snapshot.completedFocus)
    }
    @Test fun failedStartWriteDoesNotStartOrSchedule() {
        store.fail = true
        try { repo.start(); fail("Expected failed persistence") } catch (_: IOException) { }
        assertEquals(TimerStatus.IDLE, repo.peek().snapshot.status)
        assertNull(effects.scheduled)
    }
    @Test fun failedCompletionWriteDoesNotCountOrNotifyAndIsRetryable() {
        repo.start()
        clock.advance(60_000)
        store.fail = true
        try { repo.refresh(); fail("Expected failed persistence") } catch (_: IOException) { }
        assertEquals(TimerStatus.RUNNING, repo.peek().snapshot.status)
        assertEquals(0, repo.peek().snapshot.completedFocus)
        assertEquals(0, effects.completions)
        store.fail = false
        repo.refresh()
        repo.refresh()
        assertEquals(1, repo.peek().snapshot.completedFocus)
        assertEquals(1, effects.completions)
    }
    @Test fun rebootRefreshReplacesAlarmGeneration() {
        val old = repo.start().snapshot.generation
        clock.reading = ClockReading(1_025_000, 3_000, "b")
        val restored = TimerRepository(store, clock, effects)
        val rebased = restored.refresh(true)
        assertNotEquals(old, rebased.snapshot.generation)
        assertEquals(35_000L, rebased.remainingMs)
        clock.advance(40_000)
        assertEquals(TimerStatus.RUNNING, restored.alarm(old).snapshot.status)
        assertEquals(1, restored.alarm(rebased.snapshot.generation).snapshot.completedFocus)
    }
    @Test fun earlyReceiverRequestsRescheduleNotCompletion() {
        val generation = repo.start().snapshot.generation
        clock.advance(59_000)
        val early = repo.alarm(generation)
        assertEquals(1_000L, early.remainingMs)
        assertEquals(0, effects.completions)
        assertEquals(1, effects.forces)
    }
    @Test fun absentSnapshotUsesSafeDefaults() {
        val missing = MemoryStore(null)
        val newRepo = TimerRepository(missing, clock, Effects(missing))
        assertEquals(TimerStatus.IDLE, newRepo.peek().snapshot.status)
        assertEquals(1_500_000L, newRepo.peek().remainingMs)
    }
    @Test fun newSessionAfterCompletionCancelsFinishedStateAndUsesNextMode() {
        repo.start()
        clock.advance(60_000)
        repo.refresh()
        val next = repo.start().snapshot
        assertEquals(TimerMode.SHORT_BREAK, next.mode)
        assertEquals(TimerStatus.RUNNING, next.status)
        assertEquals(1, next.completedFocus)
        assertEquals(1, effects.completions)
    }
}
