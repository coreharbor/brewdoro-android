// Copyright (c) 2026 Brewdoro contributors. SPDX-License-Identifier: MIT
package io.github.coreharbor.brewdoro.ui

import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.view.WindowManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.coreharbor.brewdoro.BrewdoroApplication
import io.github.coreharbor.brewdoro.MainActivity
import io.github.coreharbor.brewdoro.R
import io.github.coreharbor.brewdoro.domain.*
import io.github.coreharbor.brewdoro.platform.CompletionNotifications
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityComfortTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val scenario get() = compose.activityRule.scenario
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as BrewdoroApplication
    private var originalPreferences = UserPreferences()
    private var originalSettings = TimerSettings()

    @Before fun prepare() {
        originalPreferences = app.userPreferences.load()
        originalSettings = app.repository.peek().snapshot.settings
        app.repository.resetCycle()
        app.repository.updateSettings(TimerSettings())
        app.userPreferences.save(UserPreferences())
        scenario.recreate()
        awaitPrimary(R.string.start)
    }
    @After fun clean() {
        // A failed external-picker assertion must not be replaced by a cleanup lifecycle error.
        if (scenario.state != Lifecycle.State.RESUMED) {
            context.startActivity(android.content.Intent(context, MainActivity::class.java).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            })
        }
        scenario.moveToState(Lifecycle.State.RESUMED)
        app.repository.resetCycle()
        app.repository.updateSettings(originalSettings)
        app.userPreferences.save(originalPreferences)
        scenario.recreate()
    }
    private fun awaitPrimary(resource: Int) {
        val label = context.getString(resource)
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("primary_action") and hasText(label)).fetchSemanticsNodes().isNotEmpty()
        }
    }
    private fun flag(): Boolean {
        var set = false
        scenario.onActivity { set = it.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0 }
        return set
    }
    private fun awaitFlag(expected: Boolean) {
        compose.waitUntil(10_000) { flag() == expected }
        assertEquals(expected, flag())
    }
    private fun livePreference(tag: String) {
        compose.onNodeWithTag(tag).performScrollTo().performClick()
        compose.waitForIdle()
    }

    @Test fun comfortSavesLiveWithoutSavingDurationDraftAndSurvivesRecreate() {
        val saved = app.repository.peek().snapshot.settings
        compose.onNodeWithTag("open_settings").performClick()
        compose.onNodeWithTag("duration_focus").performScrollTo().performTextReplacement("30")
        livePreference("keep_screen_awake")
        compose.waitUntil(10_000) { app.userPreferences.load().keepScreenAwake }
        compose.onNodeWithTag("keep_screen_awake").assertIsOn()
        livePreference("vibration_DOUBLE")
        compose.waitUntil(10_000) { app.userPreferences.load().vibration == CompletionVibration.DOUBLE }
        assertEquals(saved, app.repository.peek().snapshot.settings)
        scenario.recreate()
        compose.onNodeWithTag("settings_screen").assertExists()
        compose.onNodeWithTag("duration_focus").assertTextContains("30")
        compose.onNodeWithTag("keep_screen_awake").assertIsOn()
        compose.onNodeWithTag("vibration_DOUBLE").assertIsSelected()
        assertEquals(saved, app.repository.peek().snapshot.settings)
        compose.onNodeWithTag("close_settings").performClick()
        compose.onNodeWithTag("primary_action").performClick()
        awaitPrimary(R.string.pause)
        awaitFlag(true)
        compose.onNodeWithTag("open_settings").performClick()
        compose.onNodeWithTag("duration_focus").assertIsNotEnabled()
        compose.onNodeWithTag("keep_screen_awake").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("completion_sound").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("vibration_OFF").performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(10_000) { app.userPreferences.load().vibration == CompletionVibration.OFF }
        android.util.Log.i("BrewdoroComfort", "PASS: live comfort preferences persisted across Activity recreation; unsaved timer draft isolated; active comfort editable")
    }

    @Test fun screenFlagTracksFocusPauseResetBreakAndForegroundLifecycle() {
        awaitFlag(false)
        compose.onNodeWithTag("open_settings").performClick()
        livePreference("keep_screen_awake")
        compose.waitUntil(10_000) { app.userPreferences.load().keepScreenAwake }
        compose.onNodeWithTag("close_settings").performClick()
        compose.onNodeWithTag("primary_action").performClick()
        awaitPrimary(R.string.pause)
        awaitFlag(true)
        scenario.recreate()
        awaitPrimary(R.string.pause)
        awaitFlag(true)
        scenario.moveToState(Lifecycle.State.CREATED)
        assertFalse("Stopped Activity must clear its Window flag", flag())
        scenario.moveToState(Lifecycle.State.RESUMED)
        awaitFlag(true)
        compose.onNodeWithTag("primary_action").performClick()
        awaitPrimary(R.string.resume)
        awaitFlag(false)
        compose.onNodeWithTag("primary_action").performClick()
        awaitPrimary(R.string.pause)
        awaitFlag(true)
        compose.onNodeWithTag("reset_timer").performClick()
        awaitPrimary(R.string.start)
        awaitFlag(false)
        compose.onNodeWithTag("preset_SHORT_BREAK").performClick()
        compose.onNodeWithTag("primary_action").performClick()
        awaitPrimary(R.string.pause)
        awaitFlag(false)
        android.util.Log.i("BrewdoroComfort", "PASS: actual Window FLAG_KEEP_SCREEN_ON focus=true pause/reset/break/background=false reopen/recreate=true")
    }

    @Test fun systemSoundPickerCancelDoesNotChangePreferencesOrTimerDraft() {
        compose.onNodeWithTag("open_settings").performClick()
        compose.onNodeWithTag("duration_focus").performScrollTo().performTextReplacement("31")
        val saved = app.userPreferences.load()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        // PackageManager queries are filtered on target30+ even when launch itself succeeds.
        // Resolve as the test shell, without adding package visibility permissions to the app.
        val resolved = automation.executeShellCommand(
            "cmd package resolve-activity --brief -a android.intent.action.RINGTONE_PICKER"
        ).use { descriptor ->
            java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes().toString(Charsets.UTF_8) }
        }
        val pickerPackage = resolved.lineSequence().lastOrNull { it.contains("/") }?.substringBefore("/")?.trim()
        assertNotNull("System ringtone picker must resolve: $resolved", pickerPackage)
        compose.onNodeWithTag("completion_sound").performScrollTo().performClick()
        compose.waitUntil(20_000) {
            // A transient launcher/system window and PAUSED caller do not prove picker readiness.
            automation.rootInActiveWindow?.packageName?.toString() == pickerPackage &&
                scenario.state != Lifecycle.State.RESUMED
        }
        automation.waitForIdle(500, 5000)
        automation.executeShellCommand("input keyevent KEYCODE_BACK").use {
            java.io.FileInputStream(it.fileDescriptor).use { stream -> stream.readBytes() }
        }
        compose.waitUntil(10_000) { scenario.state == Lifecycle.State.RESUMED }
        compose.onNodeWithTag("settings_screen").assertExists()
        compose.onNodeWithTag("duration_focus").assertTextContains("31")
        assertEquals(saved, app.userPreferences.load())
        assertEquals(25, app.repository.peek().snapshot.settings.focusMinutes)
        android.util.Log.i("BrewdoroComfort", "PASS: actual system ringtone picker cancelled; comfort preference and unsaved timer draft unchanged")
    }

    @Test fun actualChannelsMatchProfilesAndPreserveExistingSystemOverrides() {
        val manager = context.getSystemService(NotificationManager::class.java)
        for (sound in listOf(CompletionSound.DEFAULT, CompletionSound.SILENT)) {
            for (vibration in listOf(CompletionVibration.OFF, CompletionVibration.SHORT, CompletionVibration.DOUBLE)) {
                val preferences = UserPreferences(sound = sound, vibration = vibration)
                val notifications = CompletionNotifications(context) { preferences }
                val id = notifications.ensureChannel()
                assertEquals(CompletionProfile.from(preferences).channelId, id)
                val channel = checkNotNull(manager.getNotificationChannel(id))
                assertEquals(AudioAttributes.USAGE_NOTIFICATION, channel.audioAttributes.usage)
                assertEquals(if (sound == CompletionSound.SILENT) null else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), channel.sound)
                assertEquals(vibration != CompletionVibration.OFF, channel.shouldVibrate())
                if (vibration != CompletionVibration.OFF) assertArrayEquals(CompletionProfile.from(preferences).vibrationPattern!!.toLongArray(), channel.vibrationPattern)
                notifications.ensureChannel()
                assertEquals(channel, manager.getNotificationChannel(id))
            }
        }
        val tones = RingtoneManager(context).apply { setType(RingtoneManager.TYPE_NOTIFICATION) }
        val tone = tones.cursor.use { cursor -> if (cursor.moveToFirst()) tones.getRingtoneUri(0) else null }
        assertNotNull("Hosted Android image must include a local system notification tone", tone)
        val customPreferences = UserPreferences(CompletionSound.CUSTOM, checkNotNull(tone).toString(), CompletionVibration.SHORT)
        val custom = CompletionNotifications(context) { customPreferences }
        assertEquals(CompletionSound.CUSTOM, custom.selectedProfile().sound)
        val customChannel = checkNotNull(manager.getNotificationChannel(custom.ensureChannel()))
        assertEquals(tone, customChannel.sound)
        assertEquals(AudioAttributes.USAGE_NOTIFICATION, customChannel.audioAttributes.usage)
        assertArrayEquals(longArrayOf(0, 180), customChannel.vibrationPattern)
        // A pre-existing channel with a system-side restriction is not rewritten by ensureChannel.
        val blockedPreferences = UserPreferences(CompletionSound.SILENT, vibration = CompletionVibration.SYSTEM)
        val id = CompletionProfile.from(blockedPreferences).channelId
        if (manager.getNotificationChannel(id) == null) {
            manager.createNotificationChannel(android.app.NotificationChannel(id, "restricted", NotificationManager.IMPORTANCE_NONE))
        }
        val restricted = CompletionNotifications(context) { blockedPreferences }
        val before = manager.getNotificationChannel(id)
        restricted.ensureChannel()
        assertEquals(before, manager.getNotificationChannel(id))
        assertFalse("enabled checks selected, not legacy channel", restricted.enabled())
        val missing = CompletionNotifications(context) { UserPreferences(CompletionSound.CUSTOM, "content://media/internal/audio/media/999999999", CompletionVibration.DOUBLE) }
        assertEquals(CompletionSound.DEFAULT, missing.selectedProfile().sound)
        assertEquals(CompletionProfile.from(UserPreferences(vibration = CompletionVibration.DOUBLE)).channelId, missing.ensureChannel())
        android.util.Log.i("BrewdoroComfort", "PASS: actual notification profile channels sound/vibration/USAGE_NOTIFICATION; selected-channel enable check; existing overrides untouched; missing URI default fallback")
    }
}
