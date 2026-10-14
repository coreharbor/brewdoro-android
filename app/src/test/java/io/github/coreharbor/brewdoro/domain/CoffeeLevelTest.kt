// Copyright (c) 2026 Brewdoro contributors. SPDX-License-Identifier: MIT
package io.github.coreharbor.brewdoro.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CoffeeLevelTest {
    @Test fun focusStartsFull() { assertEquals(1f, coffeeFillFraction(TimerMode.FOCUS, 60000L, 60000L), 0.001f) }
    @Test fun focusEndsEmpty() { assertEquals(0f, coffeeFillFraction(TimerMode.FOCUS, 0L, 60000L), 0.001f) }
    @Test fun shortBreakStartsEmpty() { assertEquals(0f, coffeeFillFraction(TimerMode.SHORT_BREAK, 60000L, 60000L), 0.001f) }
    @Test fun shortBreakEndsFull() { assertEquals(1f, coffeeFillFraction(TimerMode.SHORT_BREAK, 0L, 60000L), 0.001f) }
    @Test fun longBreakRefills() { assertEquals(0.75f, coffeeFillFraction(TimerMode.LONG_BREAK, 15000L, 60000L), 0.001f) }
    @Test fun invalidRemainingIsClamped() { assertEquals(1f, coffeeFillFraction(TimerMode.FOCUS, 90000L, 60000L), 0.001f) }
    @Test fun invalidDurationIsFinite() { assertEquals(0f, coffeeFillFraction(TimerMode.FOCUS, 0L, 0L), 0.001f) }
}
