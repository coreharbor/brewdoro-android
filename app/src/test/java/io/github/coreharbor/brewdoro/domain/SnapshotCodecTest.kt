// Copyright (c) 2026 Brewdoro contributors. SPDX-License-Identifier: MIT
package io.github.coreharbor.brewdoro.domain

import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class SnapshotCodecTest {
    private val engine = TimerEngine { "deterministic-generation" }
    private val now = ClockReading(1_000_000, 50_000, "boot-12")
    private fun roundtrip(snapshot: TimerSnapshot) {
        assertEquals(snapshot, SnapshotCodec.decode(SnapshotCodec.encode(snapshot)))
    }
    @Test fun idleRoundtrip() { roundtrip(TimerSnapshot()) }
    @Test fun runningRoundtrip() {
        roundtrip(engine.start(TimerSnapshot(settings = TimerSettings(180, 1, 37), completedFocus = 3), now).snapshot)
    }
    @Test fun pausedRoundtrip() {
        val running = engine.start(TimerSnapshot(), now).snapshot
        roundtrip(engine.pause(running, now.copy(elapsedMs = 65_123, wallMs = 1_015_123)).snapshot)
    }
    @Test fun finishedRoundtripWithCycleAndNextMode() {
        val running = engine.start(TimerSnapshot(completedFocus = 3), now).snapshot
        val finished = engine.reconcile(running, now.copy(elapsedMs = 1_550_000, wallMs = 2_500_000)).snapshot
        assertEquals(TimerMode.LONG_BREAK, finished.nextMode)
        roundtrip(finished)
    }
    @Test fun unknownBootRoundtrip() {
        roundtrip(engine.start(TimerSnapshot(), now.copy(bootId = null)).snapshot)
    }
    @Test fun nullAndGarbageRejected() {
        assertNull(SnapshotCodec.decode(null))
        assertNull(SnapshotCodec.decode(""))
        assertNull(SnapshotCodec.decode("not base64"))
        assertNull(SnapshotCodec.decode("a".repeat(4097)))
    }
    @Test fun everyTruncatedBytePrefixRejected() {
        val bytes = Base64.getDecoder().decode(SnapshotCodec.encode(TimerSnapshot()))
        for (n in bytes.indices) assertNull("prefix $n", SnapshotCodec.decode(Base64.getEncoder().encodeToString(bytes.copyOf(n))))
    }
    @Test fun unknownVersionRejected() {
        val bytes = Base64.getDecoder().decode(SnapshotCodec.encode(TimerSnapshot()))
        bytes[3] = 2
        assertNull(SnapshotCodec.decode(Base64.getEncoder().encodeToString(bytes)))
    }
    @Test fun trailingDataRejected() {
        val bytes = Base64.getDecoder().decode(SnapshotCodec.encode(TimerSnapshot())) + byteArrayOf(0)
        assertNull(SnapshotCodec.decode(Base64.getEncoder().encodeToString(bytes)))
    }
    @Test fun invalidDurationsInStoredPayloadRejected() {
        val bytes = Base64.getDecoder().decode(SnapshotCodec.encode(TimerSnapshot()))
        for (i in 4..7) bytes[i] = 0
        assertNull(SnapshotCodec.decode(Base64.getEncoder().encodeToString(bytes)))
    }
    @Test(expected = IllegalArgumentException::class) fun negativeRemainderRejected() {
        TimerSnapshot(remainingMs = -1)
    }
    @Test(expected = IllegalArgumentException::class) fun remainderBeyondDurationRejected() {
        TimerSnapshot(remainingMs = 1_500_001)
    }
    @Test(expected = IllegalArgumentException::class) fun outOfRangeCycleRejected() {
        TimerSnapshot(completedFocus = 5)
    }
    @Test(expected = IllegalArgumentException::class) fun runningWithoutDeadlinesRejected() {
        TimerSnapshot(status = TimerStatus.RUNNING)
    }
    @Test(expected = IllegalArgumentException::class) fun finishedWithoutNextRejected() {
        TimerSnapshot(status = TimerStatus.FINISHED, remainingMs = 0)
    }
    @Test(expected = IllegalArgumentException::class) fun pausedAtZeroRejected() {
        TimerSnapshot(status = TimerStatus.PAUSED, remainingMs = 0)
    }
}
