// Copyright (c) 2026 Brewdoro contributors. SPDX-License-Identifier: MIT
package io.github.coreharbor.brewdoro.ui

import kotlin.math.PI
import kotlin.math.sin

/** Original liquid box stays x14..102, y12..72. Motion vanishes at empty/full. */
object CoffeeSurface {
    const val PERIOD_MS = 5600L
    fun amplitude(level: Float): Float {
        val safe = if (level.isFinite()) level.coerceIn(0f, 1f) else 0f
        return minOf(1.25f, 60f * safe / 2f, 60f * (1f - safe) / 2f)
    }
    /** Positive-only displacement keeps every surface point inside the original liquid height. */
    fun y(level: Float, x: Float, elapsedMs: Long): Float {
        val safe = if (level.isFinite()) level.coerceIn(0f, 1f) else 0f
        val base = 72f - 60f * safe
        val phase = (elapsedMs % PERIOD_MS).toDouble() / PERIOD_MS * 2 * PI
        val spatial = (x.coerceIn(14f, 102f) - 14f) / 88f * 2 * PI
        return (base + amplitude(safe) * (0.5f + 0.5f * sin(spatial - phase).toFloat())).coerceIn(base, 72f)
    }
}
