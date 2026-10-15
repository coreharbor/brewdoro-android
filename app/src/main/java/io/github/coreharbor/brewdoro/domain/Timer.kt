// Copyright (c) 2026 Brewdoro contributors. SPDX-License-Identifier: MIT
package io.github.coreharbor.brewdoro.domain

import java.util.UUID
import kotlin.math.abs

enum class TimerMode { FOCUS, SHORT_BREAK, LONG_BREAK }
enum class TimerStatus { IDLE, RUNNING, PAUSED, FINISHED }

data class TimerSettings(
    val focusMinutes: Int = 25,
    val shortBreakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    val cycleEnabled: Boolean = true,
    val autoStartEnabled: Boolean = true,
) {
    init {
        require(focusMinutes in 1..180 && shortBreakMinutes in 1..180 && longBreakMinutes in 1..180)
    }
    fun durationMs(mode: TimerMode): Long = when (mode) {
        TimerMode.FOCUS -> focusMinutes
        TimerMode.SHORT_BREAK -> shortBreakMinutes
        TimerMode.LONG_BREAK -> longBreakMinutes
    } * 60_000L
}

data class ClockReading(val wallMs: Long, val elapsedMs: Long, val bootId: String?) {
    init { require(wallMs >= 0 && elapsedMs >= 0) }
}
fun interface TimerClock { fun now(): ClockReading }

data class TimerSnapshot(
    val settings: TimerSettings = TimerSettings(),
    val mode: TimerMode = TimerMode.FOCUS,
    val status: TimerStatus = TimerStatus.IDLE,
    val durationMs: Long = settings.durationMs(mode),
    val remainingMs: Long = durationMs,
    val completedFocus: Int = 0,
    val nextMode: TimerMode? = null,
    val generation: String = UUID.randomUUID().toString(),
    val deadlineElapsedMs: Long? = null,
    val deadlineWallMs: Long? = null,
    val bootId: String? = null,
) {
    init {
        require(durationMs in 60_000L..10_800_000L)
        require(remainingMs in 0..durationMs && completedFocus in 0..4)
        require(generation.isNotBlank() && generation.length <= 128)
        require(bootId == null || (bootId.isNotBlank() && bootId.length <= 128))
        require((status == TimerStatus.RUNNING) == (deadlineElapsedMs != null && deadlineWallMs != null))
        require(status == TimerStatus.RUNNING || (deadlineElapsedMs == null && deadlineWallMs == null && bootId == null))
        require(deadlineElapsedMs == null || deadlineElapsedMs >= 0)
        require(deadlineWallMs == null || deadlineWallMs >= 0)
        require((status == TimerStatus.FINISHED) == (nextMode != null))
        require(status != TimerStatus.FINISHED || remainingMs == 0L)
        require(status !in listOf(TimerStatus.PAUSED, TimerStatus.RUNNING) || remainingMs > 0)
        require(status != TimerStatus.IDLE || remainingMs == durationMs)
        require(deadlineElapsedMs == null || deadlineElapsedMs >= remainingMs)
        require(deadlineWallMs == null || deadlineWallMs >= remainingMs)
    }
}

data class Transition(val snapshot: TimerSnapshot, val completedMode: TimerMode? = null)

/** Deadlines, never UI ticks, are authoritative. All methods are pure except injected ID creation. */
class TimerEngine(private val newId: () -> String = { UUID.randomUUID().toString() }) {
    fun remaining(snapshot: TimerSnapshot, now: ClockReading): Long {
        if (snapshot.status != TimerStatus.RUNNING) return snapshot.remainingMs
        val sameBoot = now.bootId != null && now.bootId == snapshot.bootId
        // Elapsed regression also invalidates monotonic anchors, even with a broken boot identifier.
        val anchorElapsed = snapshot.deadlineElapsedMs!! - snapshot.remainingMs
        val deadline = if (sameBoot && now.elapsedMs >= anchorElapsed) snapshot.deadlineElapsedMs!!
            else snapshot.deadlineWallMs!!
        val current = if (sameBoot && now.elapsedMs >= anchorElapsed) now.elapsedMs else now.wallMs
        // A backward wall adjustment across reboot must not extend a session beyond its saved remainder.
        return (deadline - current).coerceIn(0L, snapshot.remainingMs)
    }

    fun reconcile(snapshot: TimerSnapshot, now: ClockReading): Transition {
        if (snapshot.status != TimerStatus.RUNNING) return Transition(snapshot)
        val left = remaining(snapshot, now)
        if (left == 0L) {
            // Linux only updates the cycle when cycling is enabled. A disabled cycle
            // finishes in place, regardless of the remembered auto-start preference.
            if (!snapshot.settings.cycleEnabled) {
                return Transition(snapshot.copy(status = TimerStatus.FINISHED, remainingMs = 0,
                    nextMode = snapshot.mode, generation = newId(), deadlineElapsedMs = null,
                    deadlineWallMs = null, bootId = null), snapshot.mode)
            }
            val count = when (snapshot.mode) {
                TimerMode.FOCUS -> (snapshot.completedFocus + 1).coerceAtMost(4)
                TimerMode.LONG_BREAK -> 0
                TimerMode.SHORT_BREAK -> snapshot.completedFocus
            }
            val next = if (snapshot.mode == TimerMode.FOCUS) {
                if (count == 4) TimerMode.LONG_BREAK else TimerMode.SHORT_BREAK
            } else TimerMode.FOCUS
            val selected = idle(snapshot.copy(completedFocus = count), next)
            // One observed completion, one fresh successor. Never replay missed sessions
            // or base its deadline on the expired predecessor (including after reboot).
            val successor = if (snapshot.settings.autoStartEnabled) selected.copy(
                status = TimerStatus.RUNNING, deadlineElapsedMs = now.elapsedMs + selected.durationMs,
                deadlineWallMs = now.wallMs + selected.durationMs, bootId = now.bootId) else selected
            return Transition(successor, snapshot.mode)
        }
        val anchorElapsed = snapshot.deadlineElapsedMs!! - snapshot.remainingMs
        if (now.bootId == null || snapshot.bootId != now.bootId || now.elapsedMs < anchorElapsed) {
            // Rebase after reboot. Old PendingIntents become harmless through a new generation.
            return Transition(snapshot.copy(remainingMs = left, deadlineElapsedMs = now.elapsedMs + left,
                deadlineWallMs = now.wallMs + left, bootId = now.bootId, generation = newId()))
        }
        if (abs(snapshot.deadlineWallMs!! - (now.wallMs + left)) > 1_000L) {
            // Clock changes within a boot never change elapsed duration, but refresh the reboot fallback.
            return Transition(snapshot.copy(remainingMs = left, deadlineWallMs = now.wallMs + left))
        }
        return Transition(snapshot)
    }

    fun start(snapshot: TimerSnapshot, now: ClockReading): Transition {
        val current = reconcile(snapshot, now)
        val s = current.snapshot
        if (s.status == TimerStatus.RUNNING || current.completedMode != null) return current
        // V1 FINISHED snapshots already counted their completion; keep them inert on
        // restore and honor their stored next mode only on an explicit manual start.
        // Cycle-off completions (including a disabled legacy cycle) restart in place.
        val mode = if (s.status == TimerStatus.FINISHED && s.settings.cycleEnabled) s.nextMode!! else s.mode
        val duration = if (s.status == TimerStatus.PAUSED) s.durationMs else s.settings.durationMs(mode)
        val left = if (s.status == TimerStatus.PAUSED) s.remainingMs else duration
        return current.copy(snapshot = s.copy(mode = mode, status = TimerStatus.RUNNING,
            durationMs = duration, remainingMs = left, nextMode = null, generation = newId(),
            deadlineElapsedMs = now.elapsedMs + left, deadlineWallMs = now.wallMs + left, bootId = now.bootId))
    }

    fun pause(snapshot: TimerSnapshot, now: ClockReading): Transition {
        val current = reconcile(snapshot, now)
        val s = current.snapshot
        // A pause at the deadline belongs to the expired phase, not its successor.
        if (s.status != TimerStatus.RUNNING || current.completedMode != null) return current
        return current.copy(snapshot = s.copy(status = TimerStatus.PAUSED, remainingMs = remaining(s, now),
            deadlineElapsedMs = null, deadlineWallMs = null, bootId = null, generation = newId()))
    }

    fun resetTimer(snapshot: TimerSnapshot, now: ClockReading): Transition {
        val current = reconcile(snapshot, now)
        return current.copy(snapshot = idle(current.snapshot, current.snapshot.mode))
    }

    fun resetCycle(snapshot: TimerSnapshot, now: ClockReading): Transition {
        val current = reconcile(snapshot, now)
        return current.copy(snapshot = idle(current.snapshot.copy(completedFocus = 0), TimerMode.FOCUS))
    }

    fun selectMode(snapshot: TimerSnapshot, mode: TimerMode, now: ClockReading): Transition {
        val current = reconcile(snapshot, now)
        if (current.snapshot.status in listOf(TimerStatus.RUNNING, TimerStatus.PAUSED)) return current
        return current.copy(snapshot = idle(current.snapshot, mode))
    }

    fun settings(snapshot: TimerSnapshot, settings: TimerSettings, now: ClockReading): Transition {
        // Apply control flags before checking an expired phase: disabling must never
        // accidentally launch one last successor. Active duration/deadlines stay fixed.
        val configured = snapshot.copy(settings = settings, completedFocus =
            if (snapshot.settings.cycleEnabled && !settings.cycleEnabled) 0 else snapshot.completedFocus)
        val current = reconcile(configured, now)
        val updated = current.snapshot
        return current.copy(snapshot = if (updated.status == TimerStatus.IDLE) idle(updated, updated.mode) else updated)
    }

    fun alarm(snapshot: TimerSnapshot, generation: String, now: ClockReading): Transition {
        // Reject before reconciliation: a stale alarm must never complete a different active session.
        return if (snapshot.status != TimerStatus.RUNNING || generation != snapshot.generation) Transition(snapshot)
            else reconcile(snapshot, now)
    }

    private fun idle(s: TimerSnapshot, mode: TimerMode) = s.copy(mode = mode, status = TimerStatus.IDLE,
        durationMs = s.settings.durationMs(mode), remainingMs = s.settings.durationMs(mode),
        nextMode = null, generation = newId(), deadlineElapsedMs = null, deadlineWallMs = null, bootId = null)
}
