// Copyright (c) 2026 Brewdoro contributors. SPDX-License-Identifier: MIT
package io.github.coreharbor.brewdoro.ui

import org.junit.Assert.*
import org.junit.Test

class CoffeeSurfaceTest {
    @Test fun surfaceAlwaysStaysInsideOriginalLiquidHeight() {
        for (percent in 0..100) for (x in 14..102) for (time in 0L..6000L step 300) {
            val level = percent / 100f
            val y = CoffeeSurface.y(level, x.toFloat(), time)
            assertTrue(y >= 72f - 60f * level && y <= 72f)
            assertTrue(CoffeeSurface.amplitude(level) in 0f..1.25f)
        }
    }
    @Test fun emptyAndFullAreExactlyUnchanged() {
        for (x in 14..102) for (time in listOf(0L, 1200L, 5600L)) {
            assertEquals(72f, CoffeeSurface.y(0f, x.toFloat(), time), 0f)
            assertEquals(12f, CoffeeSurface.y(1f, x.toFloat(), time), 0f)
        }
    }
    @Test fun midlevelChangesWithTimeAndRepeatsDeterministically() {
        assertNotEquals(CoffeeSurface.y(.5f, 30f, 0), CoffeeSurface.y(.5f, 30f, 1400))
        assertEquals(CoffeeSurface.y(.5f, 30f, 700), CoffeeSurface.y(.5f, 30f, 6300), 0f)
        assertEquals(1.25f, CoffeeSurface.amplitude(.5f), 0f)
    }
    @Test fun tinyLevelsAndInvalidInputCannotOverflow() {
        for (level in listOf(-1f, 2f, Float.NaN, Float.POSITIVE_INFINITY, .0001f, .9999f)) {
            assertTrue(CoffeeSurface.y(level, -10f, Long.MAX_VALUE).isFinite())
            assertTrue(CoffeeSurface.y(level, 1000f, 0) in 12f..72f)
        }
    }
}
