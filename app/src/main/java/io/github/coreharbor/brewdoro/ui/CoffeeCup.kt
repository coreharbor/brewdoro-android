// Copyright (c) 2026 Brewdoro contributors. SPDX-License-Identifier: MIT
package io.github.coreharbor.brewdoro.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.coreharbor.brewdoro.R

/** Direct Cairo path port: original 136 × 86 coordinates, no extra decoration or animation. */
@Composable
internal fun CoffeeCup(fractionRemaining: Float) {
    val level = fractionRemaining.coerceIn(0f, 1f)
    val description = stringResource(R.string.cup_description, (level * 100).toInt())
    Canvas(Modifier.size(136.dp, 86.dp).testTag("coffee_cup").semantics {
        contentDescription = description
    }) {
        val scale = minOf(size.width / 136f, size.height / 86f)
        withTransform({
            translate((size.width - 136f * scale) / 2f, (size.height - 86f * scale) / 2f)
            scale(scale, scale, pivot = androidx.compose.ui.geometry.Offset.Zero)
        }) {
            val body = cupBodyPath()
            clipPath(body) {
                // Linux inner_top = 12, inner_bottom = 72; inset 4 on both sides.
                val height = 60f * level
                if (height > 0f) drawPath(liquidPath(14f, 72f - height, 88f, height), LinuxPalette.Coffee)
            }
            drawPath(body, LinuxPalette.Muted, style = Stroke(2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            val handle = Path().apply {
                moveTo(106f, 26f)
                cubicTo(132f, 24f, 132f, 62f, 106f, 60f)
            }
            drawPath(handle, LinuxPalette.Muted, style = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

private fun cupBodyPath() = Path().apply {
    moveTo(15f, 8f)
    lineTo(101f, 8f)
    cubicTo(104f, 8f, 106f, 10f, 106f, 14f)
    lineTo(106f, 63f)
    cubicTo(106f, 72f, 99f, 76f, 93f, 76f)
    lineTo(23f, 76f)
    cubicTo(17f, 76f, 10f, 72f, 10f, 63f)
    lineTo(10f, 14f)
    cubicTo(10f, 10f, 12f, 8f, 15f, 8f)
    close()
}

/** Exact draw_liquid_path from Linux coffee_geometry.py (including very low levels). */
private fun liquidPath(x: Float, y: Float, width: Float, height: Float) = Path().apply {
    val topRadius = minOf(6f, height / 2f)
    val bottomRadius = minOf(10f, height / 2f)
    val right = x + width
    val bottom = y + height
    moveTo(x + topRadius, y)
    lineTo(right - topRadius, y)
    cubicTo(right, y, right, y, right, y + topRadius)
    lineTo(right, bottom - bottomRadius)
    cubicTo(right, bottom, right - bottomRadius, bottom, right - bottomRadius, bottom)
    lineTo(x + bottomRadius, bottom)
    cubicTo(x + bottomRadius, bottom, x, bottom, x, bottom - bottomRadius)
    lineTo(x, y + topRadius)
    cubicTo(x, y, x, y, x + topRadius, y)
    close()
}
