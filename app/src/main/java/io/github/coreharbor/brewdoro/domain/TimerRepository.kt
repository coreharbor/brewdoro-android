// Copyright (c) 2026 Brewdoro contributors. SPDX-License-Identifier: MIT
package io.github.coreharbor.brewdoro.domain

interface SnapshotStore {
    fun load(): TimerSnapshot?
    /** Must throw on failure, never acknowledge an uncommitted transition. */
    fun save(snapshot: TimerSnapshot)
}

interface TimerEffects {
    fun completed(mode: TimerMode)
    fun synchronize(snapshot: TimerSnapshot, force: Boolean)
}

data class TimerView(val snapshot: TimerSnapshot, val remainingMs: Long)

/** Shared by the UI and every receiver, in one process. Effects run under the same lock:
 * an old completion cannot post a notification after a concurrent new-session cancellation.
 * Commit precedes effects. A crash in between may lose a notification, never duplicate a cycle.
 */
class TimerRepository(
    private val store: SnapshotStore,
    private val clock: TimerClock,
    private val effects: TimerEffects,
    private val engine: TimerEngine = TimerEngine(),
) {
    private var snapshot = store.load() ?: TimerSnapshot()

    @Synchronized
    fun peek(): TimerView = TimerView(snapshot, engine.remaining(snapshot, clock.now()))

    @Synchronized
    fun refresh(forceAlarm: Boolean = false): TimerView = transact(forceAlarm) { s, n -> engine.reconcile(s, n) }

    @Synchronized
    fun start(): TimerView = transact { s, n -> engine.start(s, n) }

    @Synchronized
    fun pause(): TimerView = transact { s, n -> engine.pause(s, n) }

    @Synchronized
    fun resetTimer(): TimerView = transact { s, n -> engine.resetTimer(s, n) }

    @Synchronized
    fun resetCycle(): TimerView = transact { s, n -> engine.resetCycle(s, n) }

    @Synchronized
    fun selectMode(mode: TimerMode): TimerView = transact { s, n -> engine.selectMode(s, mode, n) }

    @Synchronized
    fun updateSettings(settings: TimerSettings): TimerView = transact { s, n -> engine.settings(s, settings, n) }

    @Synchronized
    fun alarm(generation: String): TimerView {
        // No reconcile or side effects for stale deliveries, even when a later timer is overdue.
        if (snapshot.status != TimerStatus.RUNNING || snapshot.generation != generation) return peek()
        return transact(true) { s, n -> engine.alarm(s, generation, n) }
    }

    private fun transact(force: Boolean = false, operation: (TimerSnapshot, ClockReading) -> Transition): TimerView {
        val now = clock.now()
        val result = operation(snapshot, now)
        if (result.snapshot != snapshot) {
            store.save(result.snapshot)
            snapshot = result.snapshot
        }
        if (result.completedMode != null && snapshot.status == TimerStatus.FINISHED) {
            effects.completed(result.completedMode)
        }
        effects.synchronize(snapshot, force)
        return TimerView(snapshot, engine.remaining(snapshot, now))
    }
}
