// Copyright (c) 2026 Brewdoro contributors. SPDX-License-Identifier: MIT
package io.github.coreharbor.brewdoro.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.coreharbor.brewdoro.R
import kotlin.math.PI
import kotlin.math.sin

/** Drawing only: elapsed time and session transitions belong to the domain. */
@Composable
internal fun CoffeeCup(fractionRemaining: Float, running: Boolean) {
    val fraction = fractionRemaining.coerceIn(0f, 1f)
    val level by animateFloatAsState(fraction, tween(600), label = "coffee level")
    // No infinite transition is kept alive while the timer is idle or paused.
    val phase = if (running) {
        val transition = rememberInfiniteTransition(label = "brewing")
        val animated by transition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart),
            label = "steam and ripples",
        )
        animated
    } else 0f
    val outline = MaterialTheme.colorScheme.primary
    val body = MaterialTheme.colorScheme.surfaceVariant
    val saucer = MaterialTheme.colorScheme.secondary
    val description = stringResource(R.string.cup_description, (fraction * 100).toInt())
    Canvas(
        Modifier.fillMaxWidth().height(220.dp).semantics { contentDescription = description },
    ) {
        val unit = minOf(size.width / 300f, size.height / 240f)
        val left = (size.width - 200f * unit) / 2f
        val top = 78f * unit
        val width = 166f * unit
        val height = 125f * unit
        val stroke = 5f * unit
        val shape = Path().apply {
            addRoundRect(RoundRect(left, top, left + width, top + height, CornerRadius(22f * unit)))
        }
        // Handle sits behind the cup so the inner edge cannot cover the coffee.
        drawRoundRect(
            color = outline,
            topLeft = Offset(left + width - 12f * unit, top + 22f * unit),
            size = Size(53f * unit, 66f * unit),
            cornerRadius = CornerRadius(23f * unit),
            style = Stroke(stroke),
        )
        drawPath(shape, body)
        clipPath(shape) {
            val waterLine = top + height * (1f - level)
            val liquid = Path().apply {
                moveTo(left, top + height)
                lineTo(left, waterLine)
                for (step in 0..40) {
                    val x = width * step / 40f
                    val ripple = if (running) sin(phase + step * 0.24f) * 2.5f * unit else 0f
                    lineTo(left + x, waterLine + ripple)
                }
                lineTo(left + width, top + height)
                close()
            }
            drawPath(liquid, Color(0xFFAB744B))
            if (level > 0.02f) {
                val foam = Path().apply {
                    for (step in 0..40) {
                        val x = width * step / 40f
                        val ripple = if (running) sin(phase + step * 0.24f) * 2.5f * unit else 0f
                        if (step == 0) moveTo(left + x, waterLine + ripple)
                        else lineTo(left + x, waterLine + ripple)
                    }
                }
                drawPath(foam, Color(0xFFF1D0A2), style = Stroke(4f * unit, cap = StrokeCap.Round))
            }
        }
        drawPath(shape, outline, style = Stroke(stroke))
        drawOval(
            saucer.copy(alpha = 0.65f),
            topLeft = Offset(left - 24f * unit, top + height + 10f * unit),
            size = Size(width + 82f * unit, 12f * unit),
        )
        for (strand in 0..2) {
            val x = left + (44 + strand * 39) * unit
            val sway = if (running) sin(phase + strand) * 5f * unit else 0f
            val steam = Path().apply {
                moveTo(x, top - 14f * unit)
                cubicTo(
                    x - 12f * unit + sway, top - 33f * unit,
                    x + 15f * unit + sway, top - 44f * unit,
                    x + sway, top - 62f * unit,
                )
            }
            drawPath(
                steam,
                outline.copy(alpha = if (running) 0.55f else 0.22f),
                style = Stroke(3f * unit, cap = StrokeCap.Round),
            )
        }
    }
}
