// Copyright (c) 2026 Brewdoro contributors. SPDX-License-Identifier: MIT
package io.github.coreharbor.brewdoro.domain

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class SnapshotMigrationTest {
    private val engine = TimerEngine { "migration-generation" }
    private val now = ClockReading(1_000_000, 100_000, "boot-original")
    // Independent V1 writer: not a V2 encode/decode roundtrip disguised as migration.
    private fun legacy(s: TimerSnapshot): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(1)
            out.writeInt(s.settings.focusMinutes)
            out.writeInt(s.settings.shortBreakMinutes)
            out.writeInt(s.settings.longBreakMinutes)
            out.writeUTF(s.mode.name)
            out.writeUTF(s.status.name)
            out.writeLong(s.durationMs)
            out.writeLong(s.remainingMs)
            out.writeInt(s.completedFocus)
            out.writeUTF(s.nextMode?.name.orEmpty())
            out.writeUTF(s.generation)
            out.writeLong(s.deadlineElapsedMs ?: -1)
            out.writeLong(s.deadlineWallMs ?: -1)
            out.writeUTF(s.bootId.orEmpty())
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray())
    }
    private fun samples(): List<TimerSnapshot> {
        val idle = TimerSnapshot(settings = TimerSettings(17, 3, 29), completedFocus = 3,
            mode = TimerMode.LONG_BREAK, generation = "original-idle")
        val running = engine.start(idle, now).snapshot
        val paused = engine.pause(running, now.copy(wallMs = now.wallMs + 12345,
            elapsedMs = now.elapsedMs + 12345)).snapshot
        val finished = TimerSnapshot(settings = idle.settings, status = TimerStatus.FINISHED,
            remainingMs = 0, completedFocus = 4, nextMode = TimerMode.LONG_BREAK,
            generation = "original-finished")
        return listOf(idle, running, paused, finished)
    }
    @Test fun v1AllStatusesPreserveEveryFieldAndEnableRequestedDefaults() {
        for (original in samples()) {
            val decoded = SnapshotCodec.decode(legacy(original))!!
            assertEquals(original, decoded)
            assertTrue(decoded.settings.cycleEnabled)
            assertTrue(decoded.settings.autoStartEnabled)
            assertEquals(decoded, SnapshotCodec.decode(SnapshotCodec.encode(decoded)))
        }
    }
    @Test fun v1RunningRestoreKeepsRemainderAndDeadlineRatherThanStartingOver() {
        val original = samples()[1]
        val migrated = SnapshotCodec.decode(legacy(original))!!
        val later = now.copy(wallMs = now.wallMs + 12_345, elapsedMs = now.elapsedMs + 12_345)
        val result = engine.reconcile(migrated, later)
        assertNull(result.completedMode)
        assertEquals(original, result.snapshot)
        assertEquals(original.durationMs - 12_345, engine.remaining(result.snapshot, later))
    }
    @Test fun v1OverdueRestoreCountsOnlyOneAndStartsFreshFullSuccessor() {
        val original = samples()[1]
        val migrated = SnapshotCodec.decode(legacy(original))!!
        val later = now.copy(wallMs = now.wallMs + 90_000_000, elapsedMs = now.elapsedMs + 90_000_000)
        val result = engine.reconcile(migrated, later)
        assertEquals(TimerMode.LONG_BREAK, result.completedMode)
        assertEquals(TimerMode.FOCUS, result.snapshot.mode)
        assertEquals(TimerStatus.RUNNING, result.snapshot.status)
        assertEquals(0, result.snapshot.completedFocus)
        assertEquals(17 * 60_000L, engine.remaining(result.snapshot, later))
        assertEquals(later.elapsedMs + 17 * 60_000, result.snapshot.deadlineElapsedMs)
    }
    @Test fun v1FinishedDoesNotRecountOrAutoStartDuringRestore() {
        val original = samples()[3]
        val decoded = SnapshotCodec.decode(legacy(original))!!
        val result = engine.reconcile(decoded, now)
        assertEquals(original, result.snapshot)
        assertNull(result.completedMode)
        val started = engine.start(decoded, now).snapshot
        assertEquals(TimerMode.LONG_BREAK, started.mode)
        assertEquals(4, started.completedFocus)
    }
    @Test fun v2AllToggleCombinationsAndStatusesRoundtrip() {
        for (cycle in listOf(false, true)) for (auto in listOf(false, true)) {
            for (s in samples()) {
                val configured = s.copy(settings = s.settings.copy(cycleEnabled = cycle, autoStartEnabled = auto))
                val encoded = SnapshotCodec.encode(configured)
                assertEquals(2, Base64.getDecoder().decode(encoded)[3].toInt())
                assertEquals(configured, SnapshotCodec.decode(encoded))
            }
        }
    }
    @Test fun v2EveryNonBooleanByteRejectedForEachToggle() {
        val valid = Base64.getDecoder().decode(SnapshotCodec.encode(TimerSnapshot()))
        for (position in 16..17) for (invalid in 2..255) {
            val bytes = valid.copyOf()
            bytes[position] = invalid.toByte()
            assertNull("invalid toggle byte", SnapshotCodec.decode(Base64.getEncoder().encodeToString(bytes)))
        }
    }
    @Test fun v1EveryTruncatedPrefixAndTrailingDataRejected() {
        val bytes = Base64.getDecoder().decode(legacy(samples()[1]))
        for (size in bytes.indices) {
            assertNull(SnapshotCodec.decode(Base64.getEncoder().encodeToString(bytes.copyOf(size))))
        }
        assertNull(SnapshotCodec.decode(Base64.getEncoder().encodeToString(bytes + byteArrayOf(0))))
    }
}
