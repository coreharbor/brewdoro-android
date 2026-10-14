// Copyright (c) 2026 Brewdoro contributors. SPDX-License-Identifier: MIT
package io.github.coreharbor.brewdoro.domain

/** Focus drains the cup; both breaks refill it. Pure logic shared by UI and tests. */
fun coffeeFillFraction(mode: TimerMode, remainingMs: Long, durationMs: Long): Float {
    val ratio = if (durationMs > 0L) remainingMs.coerceIn(0L, durationMs).toFloat() / durationMs else 0f
    return if (mode == TimerMode.FOCUS) ratio else 1f - ratio
}
