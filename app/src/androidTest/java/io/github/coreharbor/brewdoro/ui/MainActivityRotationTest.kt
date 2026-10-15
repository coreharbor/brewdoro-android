// Copyright (c) 2026 Brewdoro contributors. SPDX-License-Identifier: MIT
package io.github.coreharbor.brewdoro.ui

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.coreharbor.brewdoro.BrewdoroApplication
import io.github.coreharbor.brewdoro.MainActivity
import io.github.coreharbor.brewdoro.R
import io.github.coreharbor.brewdoro.domain.TimerMode
import io.github.coreharbor.brewdoro.domain.TimerSettings
import io.github.coreharbor.brewdoro.domain.TimerSnapshot
import io.github.coreharbor.brewdoro.domain.TimerStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real ActivityScenario configuration changes, not a stateless preview or manual UI relaunch.
 * Assert session identity/deadline, never an elapsed second or a scheduler's timing.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityRotationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val scenario: ActivityScenario<MainActivity> get() = compose.activityRule.scenario
    private var originalSettings: TimerSettings? = null

    private fun snapshot(): TimerSnapshot {
        var result: TimerSnapshot? = null
        scenario.onActivity { result = (it.application as BrewdoroApplication).repository.peek().snapshot }
        return checkNotNull(result)
    }

    private fun awaitPrimary(resource: Int) {
        val label = compose.activity.getString(resource)
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(hasTestTag("primary_action") and hasText(label)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitOrientation(orientation: Int) {
        compose.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                var matches = false
                scenario.onActivity { matches = it.resources.configuration.orientation == orientation }
                matches
            }.getOrDefault(false) // ActivityScenario can be between destroyed/resumed instances.
        }
        compose.waitForIdle()
    }

    private fun rotate(requested: Int, expected: Int) {
        val old = compose.activity
        scenario.onActivity { it.requestedOrientation = requested }
        awaitOrientation(expected)
        // A manifest configChanges shortcut would make this fail instead of masking lost state.
        assertNotSame("Rotation must really recreate MainActivity", old, compose.activity)
    }

    private fun recreate() {
        val old = compose.activity
        scenario.recreate()
        compose.waitForIdle()
        assertNotSame("ActivityScenario.recreate must replace the activity", old, compose.activity)
    }

    @Before fun normalizeFixture() {
        originalSettings = snapshot().settings
        scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            val repository = (it.application as BrewdoroApplication).repository
            repository.resetTimer()
            repository.updateSettings(TimerSettings())
            repository.selectMode(TimerMode.FOCUS)
        }
        awaitOrientation(Configuration.ORIENTATION_PORTRAIT)
        awaitPrimary(R.string.start)
        // Await actual default-duration publication, not just a possibly stale Start label.
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(hasTestTag("preset_FOCUS") and hasText("25", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @After fun cleanFixture() {
        // No live alarm, test-only duration changes or landscape lock left for later tests/captures.
        scenario.onActivity {
            val repository = (it.application as BrewdoroApplication).repository
            repository.resetTimer()
            originalSettings?.let(repository::updateSettings)
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        awaitOrientation(Configuration.ORIENTATION_PORTRAIT)
    }

    @Test fun runningSessionKeepsIdentityAndDeadlineThroughRotationAndRecreate() {
        compose.onNodeWithTag("primary_action").performScrollTo().performClick()
        awaitPrimary(R.string.pause)
        val before = snapshot()
        assertEquals(TimerStatus.RUNNING, before.status)

        rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_LANDSCAPE)
        awaitPrimary(R.string.pause)
        compose.onNodeWithTag("compact_main").assertExists()
        assertRunningSession(before)
        recreate()
        awaitPrimary(R.string.pause)
        assertRunningSession(before)
        rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Configuration.ORIENTATION_PORTRAIT)
        awaitPrimary(R.string.pause)
        assertRunningSession(before)
        compose.onNodeWithTag("primary_action").performScrollTo().performClick()
        awaitPrimary(R.string.resume)
        assertEquals(TimerStatus.PAUSED, snapshot().status)
    }

    private fun assertRunningSession(before: TimerSnapshot) {
        val after = snapshot()
        assertEquals(TimerStatus.RUNNING, after.status)
        assertEquals(before.generation, after.generation)
        assertEquals(before.deadlineElapsedMs, after.deadlineElapsedMs)
        assertEquals(before.deadlineWallMs, after.deadlineWallMs)
        assertEquals(before.bootId, after.bootId)
        assertEquals(before.mode, after.mode)
        assertEquals(before.durationMs, after.durationMs)
        assertEquals(before.completedFocus, after.completedFocus)
        assertEquals(before.settings, after.settings)
        compose.onNodeWithTag("preset_FOCUS").assertIsNotEnabled()
    }

    @Test fun pausedSessionKeepsExactRemainderThroughRotationAndRecreate() {
        compose.onNodeWithTag("primary_action").performScrollTo().performClick()
        awaitPrimary(R.string.pause)
        compose.onNodeWithTag("primary_action").performScrollTo().performClick()
        awaitPrimary(R.string.resume)
        val paused = snapshot()
        assertEquals(TimerStatus.PAUSED, paused.status)

        rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_LANDSCAPE)
        awaitPrimary(R.string.resume)
        assertEquals(paused, snapshot())
        recreate()
        awaitPrimary(R.string.resume)
        assertEquals(paused, snapshot())
        rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Configuration.ORIENTATION_PORTRAIT)
        awaitPrimary(R.string.resume)
        assertEquals(paused, snapshot())
        compose.onNodeWithTag("preset_FOCUS").assertIsNotEnabled()
        compose.onNodeWithTag("primary_action").performScrollTo().performClick()
        awaitPrimary(R.string.pause)
        assertEquals(TimerStatus.RUNNING, snapshot().status)
    }

    @Test fun settingsNavigationAndUnsavedDraftsSurviveRotationAndRecreate() {
        val saved = snapshot().settings
        compose.onNodeWithTag("open_settings").performClick()
        compose.onNodeWithTag("duration_focus").performScrollTo().performTextReplacement("30")
        compose.onNodeWithTag("duration_short").performScrollTo().performTextReplacement("7")
        compose.onNodeWithTag("duration_long").performScrollTo().performTextReplacement("20")
        assertEquals("Editing must not implicitly save", saved, snapshot().settings)

        rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_LANDSCAPE)
        assertDrafts()
        recreate()
        assertDrafts()
        rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Configuration.ORIENTATION_PORTRAIT)
        assertDrafts()
        assertEquals(saved, snapshot().settings)
        compose.onNodeWithTag("save_durations").performScrollTo().performClick()
        awaitPrimary(R.string.start)
        compose.waitUntil(timeoutMillis = 10_000) { snapshot().settings == TimerSettings(30, 7, 20) }
        compose.onNodeWithTag("preset_FOCUS").assertTextContains("30", substring = true)
        compose.onNodeWithTag("open_settings").performClick()
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("settings_screen").assertDoesNotExist()
        recreate()
        compose.onNodeWithTag("minimal_main").assertIsDisplayed()
        compose.onNodeWithTag("settings_screen").assertDoesNotExist()
    }

    private fun assertDrafts() {
        compose.onNodeWithTag("settings_screen").assertIsDisplayed()
        compose.onNodeWithTag("minimal_main").assertDoesNotExist()
        compose.onNodeWithTag("close_settings").assertIsDisplayed()
        compose.onNodeWithTag("duration_focus").assertTextContains("30")
        compose.onNodeWithTag("duration_short").assertTextContains("7")
        compose.onNodeWithTag("duration_long").assertTextContains("20")
        compose.onNodeWithTag("save_durations").assertIsEnabled()
    }
}
