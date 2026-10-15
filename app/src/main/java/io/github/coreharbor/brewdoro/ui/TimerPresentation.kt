// Copyright (c) 2026 Brewdoro contributors. SPDX-License-Identifier: MIT
package io.github.coreharbor.brewdoro.ui

import io.github.coreharbor.brewdoro.domain.TimerMode
import io.github.coreharbor.brewdoro.domain.TimerSnapshot
import io.github.coreharbor.brewdoro.domain.TimerStatus

internal data class TimerDisplay(val minutes: Int, val seconds: Int)

/** Display-only rounding; deadlines and transitions remain in the unchanged timer engine. */
internal fun timerDisplay(remainingMs: Long, durationMs: Long): TimerDisplay {
    val remaining = remainingMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
    val seconds = remaining / 1000L + if (remaining % 1000L > 0) 1L else 0L
    return TimerDisplay((seconds / 60L).toInt(), (seconds % 60L).toInt())
}

/** Linux position_for semantics; a finished focus has already incremented completedFocus. */
internal fun sessionPosition(state: TimerSnapshot): Int = when {
    state.mode == TimerMode.FOCUS && state.status != TimerStatus.FINISHED -> (state.completedFocus + 1).coerceIn(1, 4)
    else -> state.completedFocus.coerceIn(1, 4)
}

internal fun validMinutes(value: Int?): Boolean = value != null && value in 1..180
