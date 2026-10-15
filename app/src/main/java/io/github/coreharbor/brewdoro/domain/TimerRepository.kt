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
    /** Explicit user start/reset only; ticks and forced restore must retain completion alerts. */
    fun dismissCompletion() {}
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
    fun start(): TimerView = transact(dismissOnStart = true) { s, n -> engine.start(s, n) }

    @Synchronized
    fun pause(expectedGeneration: String? = null): TimerView {
        // The UI can bind a click to its rendered phase, avoiding an alarm-vs-click
        // race that would otherwise pause a just-started successor.
        if (expectedGeneration != null && expectedGeneration != snapshot.generation) return peek()
        return transact { s, n -> engine.pause(s, n) }
    }

    @Synchronized
    fun resetTimer(): TimerView = transact(dismiss = true) { s, n -> engine.resetTimer(s, n) }

    @Synchronized
    fun resetCycle(): TimerView = transact(dismiss = true) { s, n -> engine.resetCycle(s, n) }

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

    private fun transact(
        force: Boolean = false,
        dismiss: Boolean = false,
        dismissOnStart: Boolean = false,
        operation: (TimerSnapshot, ClockReading) -> Transition,
    ): TimerView {
        val now = clock.now()
        val result = operation(snapshot, now)
        val changed = result.snapshot != snapshot
        if (changed) {
            store.save(result.snapshot)
            snapshot = result.snapshot
        }
        // Persist first, then schedule/cancel the successor before showing the alert.
        // Synchronization never withdraws it on a later tick, force refresh or reopen.
        effects.synchronize(snapshot, force)
        if (dismiss || (dismissOnStart && changed && result.completedMode == null)) {
            effects.dismissCompletion()
        }
        // A user reset stops the chain and withdraws the alert even if it observed
        // expiry while reconciling (the completion count is still committed once).
        if (result.completedMode != null && !dismiss) effects.completed(result.completedMode)
        return TimerView(snapshot, engine.remaining(snapshot, now))
    }
}
