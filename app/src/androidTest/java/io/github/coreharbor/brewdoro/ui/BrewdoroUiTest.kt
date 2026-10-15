// Copyright (c) 2026 Brewdoro contributors. SPDX-License-Identifier: MIT
package io.github.coreharbor.brewdoro.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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

    private fun launch(compact: Boolean = false) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (compact) 2f else 1f)) {
                Box(if (compact) Modifier.size(280.dp, 360.dp) else Modifier) {
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
