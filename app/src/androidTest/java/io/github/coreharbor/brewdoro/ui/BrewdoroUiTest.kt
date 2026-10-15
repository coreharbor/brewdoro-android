// Copyright (c) 2026 Brewdoro contributors. SPDX-License-Identifier: MIT
package io.github.coreharbor.brewdoro.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.coreharbor.brewdoro.domain.ClockReading
import io.github.coreharbor.brewdoro.domain.TimerEngine
import io.github.coreharbor.brewdoro.domain.TimerMode
import io.github.coreharbor.brewdoro.domain.TimerSnapshot
import io.github.coreharbor.brewdoro.domain.TimerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Deterministic UI smoke coverage: real Compose controls, no alarm timing or permission dialogs. */
@RunWith(AndroidJUnit4::class)
class BrewdoroUiTest {
    @get:Rule val compose = createComposeRule()
    private val engine = TimerEngine()
    private val now = ClockReading(1_000_000L, 100_000L, "ui-test-boot")
    private var state by mutableStateOf(TimerSnapshot())
    private var notifications = 0
    private var exactAlarms = 0
    private var languages = 0
    private var cycleResets = 0

    private fun launch(compact: Boolean = false, viewportWidth: Dp? = null, viewportHeight: Dp? = null, fontScale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current
            BoxWithConstraints(Modifier.fillMaxSize()) {
                // Fit an explicit logical viewport inside any emulator root, without clipping a
                // requiredSize bigger than the physical screen. Assertions use real laid-out nodes.
                val fit = if (viewportWidth != null && viewportHeight != null)
                    minOf(1f, maxWidth.value / viewportWidth.value, maxHeight.value / viewportHeight.value) else 1f
                // Half-step densities keep the 48dp targets and original 136 × 86dp cup
                // on integral pixels even when the host is a portrait emulator.
                val fittedDensity = if (viewportWidth != null)
                    (density.density * fit * 2).toInt().coerceAtLeast(1) / 2f else density.density
                CompositionLocalProvider(LocalDensity provides Density(fittedDensity, if (compact) 2f else fontScale)) {
                    Box((if (viewportWidth != null && viewportHeight != null) Modifier.size(viewportWidth, viewportHeight)
                        else if (compact) Modifier.size(280.dp, 360.dp) else Modifier)
                        .consumeWindowInsets(WindowInsets.safeDrawing).testTag("test_viewport")) {
                        BrewdoroApp(
                            state = state, remainingMs = state.remainingMs,
                            notificationEnabled = false, exactAlarmsEnabled = false,
                            onStart = { state = engine.start(state, now).snapshot },
                            onPause = { state = engine.pause(state, now).snapshot },
                            onResetTimer = { state = engine.resetTimer(state, now).snapshot },
                            onResetCycle = { cycleResets++; state = engine.resetCycle(state, now).snapshot },
                            onMode = { state = engine.selectMode(state, it, now).snapshot },
                            onSettings = { state = engine.settings(state, it, now).snapshot },
                            onNotifications = { notifications++ }, onExactAlarms = { exactAlarms++ },
                            onLanguage = { languages++ },
                        )
                    }
                }
            }
        }
    }

    private fun scrollMaximum(): Float = compose.onNodeWithTag("main_scroll")
        .fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].maxValue()

    private fun assertFullyInsideViewport(tag: String) {
        val viewportTag = if (tag == "open_settings" || tag == "close_settings") "test_viewport" else "minimal_main"
        val viewport = compose.onNodeWithTag(viewportTag).getUnclippedBoundsInRoot()
        val node = compose.onNodeWithTag(tag).assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue("$tag must be fully on screen, not merely intersect it: $node within $viewport",
            node.left >= viewport.left - 0.5.dp && node.top >= viewport.top - 0.5.dp &&
                node.right <= viewport.right + 0.5.dp && node.bottom <= viewport.bottom + 0.5.dp)
    }

    @Test fun landscapeShowsWholeCupReadoutAndAllControlsWithoutScrolling() {
        // 760 × 320dp inset-free app content, including the fixed 48dp gear header.
        launch(viewportWidth = 760.dp, viewportHeight = 320.dp)
        val idle = TimerSnapshot()
        val running = engine.start(idle, now).snapshot
        val paused = engine.pause(running, now).snapshot
        val finished = engine.reconcile(running,
            now.copy(wallMs = now.wallMs + idle.durationMs, elapsedMs = now.elapsedMs + idle.durationMs)).snapshot
        val shortBreak = engine.selectMode(idle, TimerMode.SHORT_BREAK, now).snapshot
        val longBreak = engine.selectMode(idle, TimerMode.LONG_BREAK, now).snapshot
        for (snapshot in listOf(idle, running, paused, finished, shortBreak, longBreak)) {
            compose.runOnIdle { state = snapshot }
            compose.onNodeWithTag("compact_main").assertExists()
            assertEquals("Ordinary landscape must not require a scroll", 0f, scrollMaximum(), 0.5f)
            for (tag in listOf("open_settings", "mode_label", "session_label", "timer_digits", "coffee_cup",
                "primary_action", "reset_timer", "preset_FOCUS", "preset_SHORT_BREAK", "preset_LONG_BREAK")) {
                assertFullyInsideViewport(tag)
            }
            val cup = compose.onNodeWithTag("coffee_cup").getUnclippedBoundsInRoot()
            val readout = compose.onNodeWithTag("timer_digits").getUnclippedBoundsInRoot()
            assertTrue("Cup stays in left column; readout stays right", cup.right < readout.left)
            compose.onNodeWithTag("coffee_cup").assertWidthIsEqualTo(136.dp).assertHeightIsEqualTo(86.dp)
            for (tag in listOf("open_settings", "primary_action", "reset_timer", "preset_FOCUS", "preset_SHORT_BREAK", "preset_LONG_BREAK")) {
                compose.onNodeWithTag(tag).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
            }
        }
        compose.runOnIdle { state = idle }
        compose.onNodeWithTag("preset_LONG_BREAK").performClick().assertIsSelected()
        compose.onNodeWithTag("primary_action").performClick()
        compose.runOnIdle { assertEquals(TimerStatus.RUNNING, state.status) }
        compose.onNodeWithTag("primary_action").performClick()
        compose.runOnIdle { assertEquals(TimerStatus.PAUSED, state.status) }
        compose.onNodeWithTag("reset_timer").performClick()
        compose.runOnIdle { assertEquals(TimerStatus.IDLE, state.status) }
    }

    @Test fun veryShortLandscapeFallsBackToScrollWithSettingsStillReachable() {
        launch(viewportWidth = 760.dp, viewportHeight = 220.dp)
        assertLandscapeFallback()
    }

    @Test fun landscapeLargeFontFallsBackToScrollWithoutShrinkingTouchTargets() {
        launch(viewportWidth = 760.dp, viewportHeight = 300.dp, fontScale = 2f)
        assertLandscapeFallback()
        val primary = compose.onNodeWithTag("primary_action").getUnclippedBoundsInRoot()
        val reset = compose.onNodeWithTag("reset_timer").getUnclippedBoundsInRoot()
        assertTrue("Large-font labels use full-width stacked actions", primary.bottom <= reset.top)
        assertEquals(primary.width.value, reset.width.value, 0.5f)
    }

    private fun assertLandscapeFallback() {
        compose.onNodeWithTag("compact_main").assertExists()
        assertTrue("Overflow must be scrollable, not clipped", scrollMaximum() > 0f)
        for (tag in listOf("coffee_cup", "mode_label", "session_label", "timer_digits", "primary_action", "reset_timer", "preset_LONG_BREAK")) {
            compose.onNodeWithTag(tag).performScrollTo()
            assertFullyInsideViewport(tag)
        }
        for (tag in listOf("primary_action", "reset_timer", "preset_FOCUS", "preset_SHORT_BREAK", "preset_LONG_BREAK")) {
            compose.onNodeWithTag(tag).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        }
        compose.onNodeWithTag("preset_LONG_BREAK").performScrollTo().performClick()
        compose.onNodeWithTag("primary_action").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(TimerStatus.RUNNING, state.status) }
        assertFullyInsideViewport("open_settings")
        compose.onNodeWithTag("open_settings").performClick()
        compose.onNodeWithTag("reset_cycle").performScrollTo().assertIsDisplayed()
        assertFullyInsideViewport("close_settings")
        compose.onNodeWithTag("close_settings").performClick()
        compose.onNodeWithTag("reset_timer").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(TimerStatus.IDLE, state.status) }
    }

    @Test fun mainIsMinimalInLinuxOrderWithHorizontalAccessiblePresets() {
        launch()
        for (tag in listOf("mode_label", "session_label", "timer_digits", "coffee_cup", "primary_action", "reset_timer", "duration_presets")) {
            compose.onNodeWithTag(tag).assertIsDisplayed()
        }
        for (tag in listOf("settings_screen", "duration_focus", "notification_settings", "exact_alarm_settings", "reset_cycle")) {
            compose.onNodeWithTag(tag).assertDoesNotExist()
        }
        val order = listOf("mode_label", "session_label", "timer_digits", "coffee_cup", "primary_action", "reset_timer", "duration_presets")
        val bounds = order.map { compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot }
        bounds.zipWithNext().forEach { (above, below) -> assertTrue("Linux vertical order", above.bottom <= below.top + 1f) }
        compose.onNodeWithTag("coffee_cup").assertWidthIsEqualTo(136.dp).assertHeightIsEqualTo(86.dp)
        val presets = listOf("preset_FOCUS", "preset_SHORT_BREAK", "preset_LONG_BREAK")
        val presetBounds = presets.map {
            compose.onNodeWithTag(it).assertIsEnabled().assertHeightIsAtLeast(48.dp).fetchSemanticsNode().boundsInRoot
        }
        assertEquals(presetBounds[0].top, presetBounds[1].top, 1f)
        assertEquals(presetBounds[1].top, presetBounds[2].top, 1f)
        assertTrue(presetBounds[0].right < presetBounds[1].left)
        assertTrue(presetBounds[1].right < presetBounds[2].left)
        compose.onNodeWithTag("preset_FOCUS").assertIsSelected()
        compose.onNodeWithTag("open_settings").assertHeightIsAtLeast(48.dp).assertIsEnabled()
        compose.onNodeWithTag("primary_action").assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("reset_timer").assertHeightIsAtLeast(48.dp)
    }

    @Test fun startPauseResetAndPresetControlsRemainFunctional() {
        launch()
        compose.onNodeWithTag("preset_SHORT_BREAK").performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(TimerMode.SHORT_BREAK, state.mode) }
        compose.onNodeWithTag("primary_action").performClick()
        compose.runOnIdle { assertEquals(TimerStatus.RUNNING, state.status) }
        compose.onNodeWithTag("preset_FOCUS").assertIsNotEnabled()
        compose.onNodeWithTag("primary_action").performClick()
        compose.runOnIdle { assertEquals(TimerStatus.PAUSED, state.status) }
        compose.onNodeWithTag("preset_FOCUS").assertIsNotEnabled()
        compose.onNodeWithTag("reset_timer").performClick()
        compose.runOnIdle { assertEquals(TimerStatus.IDLE, state.status) }
        compose.onNodeWithTag("preset_FOCUS").assertIsEnabled().performClick().assertIsSelected()
    }

    @Test fun runningSettingsLockOnlyDurationsNotPermissionControlsOrCycleReset() {
        launch()
        compose.onNodeWithTag("primary_action").performClick()
        compose.onNodeWithTag("open_settings").assertIsEnabled().performClick()
        compose.onNodeWithTag("settings_screen").assertIsDisplayed()
        compose.onNodeWithTag("minimal_main").assertDoesNotExist()
        for (tag in listOf("duration_focus", "duration_short", "duration_long", "save_durations")) {
            compose.onNodeWithTag(tag).assertIsNotEnabled()
        }
        compose.onNodeWithTag("notification_settings").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithTag("exact_alarm_settings").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithTag("language_settings").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, notifications); assertEquals(1, exactAlarms); assertEquals(1, languages) }
        compose.onNodeWithTag("reset_cycle").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithTag("cycle_confirmation").assertIsDisplayed()
        compose.onNodeWithTag("cancel_reset_cycle").performClick()
        compose.runOnIdle { assertEquals(0, cycleResets); assertEquals(TimerStatus.RUNNING, state.status) }
        compose.onNodeWithTag("reset_cycle").performScrollTo().performClick()
        compose.onNodeWithTag("confirm_reset_cycle").performClick()
        compose.runOnIdle { assertEquals(1, cycleResets); assertEquals(TimerStatus.IDLE, state.status) }
        compose.onNodeWithTag("duration_focus").assertIsEnabled()
        compose.onNodeWithTag("close_settings").performClick()
        compose.onNodeWithTag("minimal_main").assertIsDisplayed()
        compose.onNodeWithTag("notification_settings").assertDoesNotExist()
    }

    @Test fun durationDraftValidationAndSaveUpdatePresets() {
        launch()
        compose.onNodeWithTag("open_settings").performClick()
        compose.onNodeWithTag("duration_focus").performTextReplacement("0")
        compose.onNodeWithTag("save_durations").assertIsNotEnabled()
        compose.onNodeWithTag("duration_focus").performTextReplacement("30")
        compose.onNodeWithTag("save_durations").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(30, state.settings.focusMinutes) }
        compose.onNodeWithTag("settings_screen").assertDoesNotExist()
        compose.onNodeWithTag("preset_FOCUS").assertTextContains("30", substring = true)
    }

    @Test fun compactLargeFontLayoutScrollsToControlsAndSettings() {
        launch(compact = true)
        compose.onNodeWithTag("preset_LONG_BREAK").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(TimerMode.LONG_BREAK, state.mode) }
        compose.onNodeWithTag("primary_action").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("open_settings").assertIsDisplayed().performClick()
        compose.onNodeWithTag("reset_cycle").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("close_settings").assertIsDisplayed().performClick()
        compose.onNodeWithTag("reset_timer").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(TimerStatus.IDLE, state.status) }
    }
}
