// Copyright (c) 2026 Brewdoro contributors. SPDX-License-Identifier: MIT
package io.github.coreharbor.brewdoro.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.coreharbor.brewdoro.MainActivity
import io.github.coreharbor.brewdoro.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Also exercise the installed activity and its real asynchronous repository callbacks. */
@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun awaitPrimary(label: String) {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(hasTestTag("primary_action") and hasText(label)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun installedActivityIsMinimalAndGearWorksDuringRealSession() {
        val start = compose.activity.getString(R.string.start)
        val pause = compose.activity.getString(R.string.pause)
        val resume = compose.activity.getString(R.string.resume)
        compose.onNodeWithTag("minimal_main").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.app_name)).assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.app_tagline)).assertDoesNotExist()
        compose.onNodeWithTag("notification_settings").assertDoesNotExist()
        // Normalize persisted state too, so rerunning against an already installed app is safe.
        compose.onNodeWithTag("reset_timer").performScrollTo().performClick()
        awaitPrimary(start)
        compose.onNodeWithTag("preset_FOCUS").performScrollTo().performClick()
        try {
            compose.onNodeWithTag("primary_action").performScrollTo().performClick()
            awaitPrimary(pause)
            compose.onNodeWithTag("preset_FOCUS").assertIsNotEnabled()
            compose.onNodeWithTag("open_settings").performClick()
            compose.onNodeWithTag("minimal_main").assertDoesNotExist()
            compose.onNodeWithTag("duration_focus").assertIsNotEnabled()
            compose.onNodeWithTag("notification_settings").performScrollTo().assertIsEnabled()
            compose.onNodeWithTag("exact_alarm_settings").performScrollTo().assertIsEnabled()
            compose.onNodeWithTag("close_settings").performClick()
            compose.onNodeWithTag("primary_action").performScrollTo().performClick()
            awaitPrimary(resume)
            compose.onNodeWithTag("reset_timer").performScrollTo().performClick()
            awaitPrimary(start)
            compose.onNodeWithTag("preset_FOCUS").assertIsEnabled()
        } finally {
            // Leave no active alarm for the parent's post-test actual-activity screenshot.
            compose.onAllNodesWithTag("close_settings").fetchSemanticsNodes().firstOrNull()?.let {
                compose.onNodeWithTag("close_settings").performClick()
            }
            compose.onNodeWithTag("reset_timer").performScrollTo().performClick()
            awaitPrimary(start)
        }
    }
}
