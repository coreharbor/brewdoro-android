// Copyright (c) 2026 Brewdoro contributors. SPDX-License-Identifier: MIT
package io.github.coreharbor.brewdoro.ui

import io.github.coreharbor.brewdoro.domain.TimerMode
import io.github.coreharbor.brewdoro.domain.TimerSnapshot
import io.github.coreharbor.brewdoro.domain.TimerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerPresentationTest {
    @Test fun displayRoundsUpWithoutChangingTimerState() {
        assertEquals(TimerDisplay(25, 0), timerDisplay(1_500_000L, 1_500_000L))
        assertEquals(TimerDisplay(1, 1), timerDisplay(60_001L, 1_500_000L))
        assertEquals(TimerDisplay(0, 1), timerDisplay(1L, 1_500_000L))
        assertEquals(TimerDisplay(0, 0), timerDisplay(0L, 1_500_000L))
        assertEquals(TimerDisplay(180, 0), timerDisplay(10_800_000L, 10_800_000L))
    }

    @Test fun displayClampsUnexpectedInputs() {
        assertEquals(TimerDisplay(0, 0), timerDisplay(-1L, 60_000L))
        assertEquals(TimerDisplay(1, 0), timerDisplay(Long.MAX_VALUE, 60_000L))
        assertEquals(TimerDisplay(0, 0), timerDisplay(10L, -1L))
    }

    @Test fun cyclePositionMatchesLinuxWithoutDoubleCountingFinishedFocus() {
        for (completed in 0..4) {
            assertEquals((completed + 1).coerceAtMost(4), sessionPosition(TimerSnapshot(completedFocus = completed)))
            for (mode in listOf(TimerMode.SHORT_BREAK, TimerMode.LONG_BREAK)) {
                assertEquals(completed.coerceAtLeast(1), sessionPosition(TimerSnapshot(mode = mode, completedFocus = completed)))
            }
        }
        assertEquals(1, sessionPosition(TimerSnapshot(status = TimerStatus.FINISHED, remainingMs = 0,
            nextMode = TimerMode.SHORT_BREAK, completedFocus = 1)))
        assertEquals(4, sessionPosition(TimerSnapshot(status = TimerStatus.FINISHED, remainingMs = 0,
            nextMode = TimerMode.LONG_BREAK, completedFocus = 4)))
    }

    @Test fun durationDraftRangeIsBounded() {
        assertFalse(validMinutes(null))
        assertFalse(validMinutes(0))
        assertFalse(validMinutes(181))
        assertTrue(validMinutes(1))
        assertTrue(validMinutes(180))
    }
}
