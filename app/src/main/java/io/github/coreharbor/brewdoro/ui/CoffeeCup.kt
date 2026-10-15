// Copyright (c) 2026 Brewdoro contributors. SPDX-License-Identifier: MIT
package io.github.coreharbor.brewdoro.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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

/** Original Cairo outline and palette; only the clipped liquid surface moves. */
@Composable
internal fun CoffeeCup(fractionRemaining: Float, animate: Boolean = false) {
    val level = if (fractionRemaining.isFinite()) fractionRemaining.coerceIn(0f, 1f) else 0f
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var started by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> started = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    // Observe changes while visible too: accessibility/animation scale can change without recreation.
    val scale by produceState(initialValue = animationScale(context), context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { value = animationScale(context) }
        }
        val registered = runCatching {
            context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
            true
        }.getOrDefault(false)
        awaitDispose { if (registered) context.contentResolver.unregisterContentObserver(observer) }
    }
    val moving = animate && started && scale > 0f && level > 0f && level < 1f
    // InfiniteTransition is intentionally conditional: no work/idling loop when disabled or hidden.
    val frame = if (moving) {
        val transition = rememberInfiniteTransition(label = "coffee_surface")
        transition.animateFloat(0f, CoffeeSurface.PERIOD_MS.toFloat(),
            infiniteRepeatable(tween(CoffeeSurface.PERIOD_MS.toInt(), easing = LinearEasing), RepeatMode.Restart),
            label = "coffee_phase")
    } else remember { mutableStateOf(0f) }
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
                if (height > 0f) {
                    val liquid = liquidPath(14f, 72f - height, 88f, height)
                    if (moving) clipPath(liquid) {
                        val surface = Path().apply {
                            moveTo(14f, CoffeeSurface.y(level, 14f, frame.value.toLong()))
                            for (x in 15..102) lineTo(x.toFloat(), CoffeeSurface.y(level, x.toFloat(), frame.value.toLong()))
                            lineTo(102f, 72f)
                            lineTo(14f, 72f)
                            close()
                        }
                        drawPath(surface, LinuxPalette.Coffee)
                    } else drawPath(liquid, LinuxPalette.Coffee)
                }
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

private fun animationScale(context: android.content.Context): Float =
    runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(0f)
