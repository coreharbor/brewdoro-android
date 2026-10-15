// Copyright (c) 2026 Brewdoro contributors. SPDX-License-Identifier: MIT
package io.github.coreharbor.brewdoro.ui

import android.app.Notification
import android.app.NotificationManager
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.coreharbor.brewdoro.BrewdoroApplication
import io.github.coreharbor.brewdoro.MainActivity
import io.github.coreharbor.brewdoro.R
import io.github.coreharbor.brewdoro.domain.SnapshotCodec
import io.github.coreharbor.brewdoro.domain.TimerMode
import io.github.coreharbor.brewdoro.domain.TimerSettings
import io.github.coreharbor.brewdoro.domain.TimerStatus
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real one-minute deadlines: no injected completion, fake clock, receiver broadcast or refresh
 * in the waiting predicate. Background phase can advance only through AlarmManager/receiver.
 * Hosted CI pregrants notification and exact-alarm access to the test install.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityAutoCycleTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as BrewdoroApplication
    private var original: TimerSettings? = null

    private fun awaitPrimary(resource: Int) {
        val label = context.getString(resource)
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(hasTestTag("primary_action") and hasText(label)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Before fun configureShortCycle() {
        original = app.repository.peek().snapshot.settings
        app.repository.resetCycle()
        app.repository.updateSettings(TimerSettings(1, 1, 1, cycleEnabled = true, autoStartEnabled = true))
        assertTrue("CI must allow exact alarms for the actual receiver test", app.alarms.exactAllowed())
        assertTrue("CI must grant notifications for completion evidence", app.notifications.enabled())
        awaitPrimary(R.string.start)
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(hasTestTag("preset_FOCUS") and hasText("1", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @After fun cleanup() {
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        app.repository.resetCycle()
        original?.let { app.repository.updateSettings(it) }
    }

    private fun awaitStage(mode: TimerMode, previousGeneration: String) {
        compose.waitUntil(timeoutMillis = 95_000) {
            // peek computes remaining but does not reconcile or advance the timer.
            val s = app.repository.peek().snapshot
            s.status == TimerStatus.RUNNING && s.mode == mode && s.generation != previousGeneration
        }
    }

    private fun assertNotification(resource: Int) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val expected = context.getString(resource)
        compose.waitUntil(timeoutMillis = 10_000) {
            manager.activeNotifications.any { it.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() == expected }
        }
    }

    @Test fun startsSuccessorsInForegroundAndWithActivityStoppedWithoutAnotherTap() {
        val started = SystemClock.elapsedRealtime()
        compose.onNodeWithTag("primary_action").performScrollTo().performClick()
        awaitPrimary(R.string.pause)
        val first = app.repository.peek().snapshot
        assertEquals(TimerMode.FOCUS, first.mode)
        assertEquals(TimerStatus.RUNNING, first.status)

        awaitStage(TimerMode.SHORT_BREAK, first.generation)
        val shortBreak = app.repository.peek().snapshot
        assertEquals(1, shortBreak.completedFocus)
        assertEquals(60_000L, shortBreak.durationMs)
        assertNotNull(shortBreak.deadlineElapsedMs)
        assertNotification(R.string.notification_focus_done)
        // Repository/receiver commit can precede the next one-second Activity refresh.
        // Pause is already visible on the old running phase, so await actual successor semantics.
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(hasTestTag("preset_SHORT_BREAK") and isSelected()).fetchSemanticsNodes().isNotEmpty()
        }
        awaitPrimary(R.string.pause)
        compose.onNodeWithTag("preset_SHORT_BREAK").assertIsSelected().assertIsNotEnabled()

        // Stop Activity (and repeatOnLifecycle UI ticks), without pausing the running session.
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        assertEquals(Lifecycle.State.CREATED, compose.activityRule.scenario.state)
        awaitStage(TimerMode.FOCUS, shortBreak.generation)
        val nextFocus = app.repository.peek().snapshot
        assertEquals(1, nextFocus.completedFocus)
        assertEquals(60_000L, nextFocus.durationMs)
        assertNotNull(nextFocus.deadlineElapsedMs)
        assertNotification(R.string.notification_break_done)
        val stored = context.getSharedPreferences("timer", android.content.Context.MODE_PRIVATE).getString("snapshot", null)
        assertEquals("Receiver commits successor before publishing it", nextFocus, SnapshotCodec.decode(stored))
        File(context.filesDir, "auto-cycle-evidence.txt").writeText(
            "PASS: real60s focus -> running short break in foreground; real60s short break -> running focus with Activity CREATED. " +
                "No refresh in poll, no manual next-stage tap. Both notifications observed; receiver successor persisted. " +
                "elapsedMs=" + (SystemClock.elapsedRealtime() - started))

        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(hasTestTag("preset_FOCUS") and isSelected()).fetchSemanticsNodes().isNotEmpty()
        }
        awaitPrimary(R.string.pause)
        compose.onNodeWithTag("primary_action").performScrollTo().performClick()
        awaitPrimary(R.string.resume)
        assertEquals(TimerStatus.PAUSED, app.repository.peek().snapshot.status)
        compose.onNodeWithTag("reset_timer").performScrollTo().performClick()
        awaitPrimary(R.string.start)
        assertEquals(TimerStatus.IDLE, app.repository.peek().snapshot.status)
    }
}
