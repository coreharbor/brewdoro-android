// Copyright (c) 2026 Brewdoro contributors. SPDX-License-Identifier: MIT
package io.github.coreharbor.brewdoro.domain

import org.junit.Assert.*
import org.junit.Test

class UserPreferencesTest {
    @Test fun defaultsPreserveLegacyChannelAndDoNotKeepScreenAwake() {
        assertEquals(UserPreferences(), UserPreferencesCodec.decode(null))
        assertFalse(UserPreferences().keepScreenAwake)
        assertEquals("session_completion", CompletionProfile.from(UserPreferences()).channelId)
        assertNull(CompletionProfile.from(UserPreferences()).vibrationPattern)
    }
    @Test fun invalidVersionAndMalformedRecordsUseSafeDefaults() {
        for (raw in listOf("", "garbage", "2\nDEFAULT\nOFF\ntrue\n", "1\nDEFAULT"))
            assertEquals(UserPreferences(), UserPreferencesCodec.decode(raw))
    }
    @Test fun invalidFieldsAreSanitizedIndependently() {
        assertEquals(UserPreferences(keepScreenAwake = true), UserPreferencesCodec.decode("1\nUNKNOWN\nNOPE\ntrue\n"))
        assertEquals(UserPreferences(), UserPreferencesCodec.decode("1\nCUSTOM\nSYSTEM\nTRUE\nhttps://remote/tone"))
        assertEquals(CompletionSound.DEFAULT, UserPreferences(sound = CompletionSound.CUSTOM, soundUri = "file:///tone").normalized().sound)
        assertFalse(validToneUri("content://tone\ninjected"))
    }
    @Test fun everyChoiceRoundTripsWithoutChangingTimerSnapshot() {
        for (sound in CompletionSound.entries) for (vibration in CompletionVibration.entries) for (awake in listOf(false, true)) {
            val value = UserPreferences(sound, if (sound == CompletionSound.CUSTOM) "content://media/internal/audio/media/7" else null, vibration, awake)
            assertEquals(value, UserPreferencesCodec.decode(UserPreferencesCodec.encode(value)))
        }
    }
    @Test fun profileIdentityIsStableAndIndependentOfScreenPreference() {
        val value = UserPreferences(vibration = CompletionVibration.DOUBLE)
        val a = CompletionProfile.from(value)
        assertEquals(a.channelId, CompletionProfile.from(value.copy(keepScreenAwake = true)).channelId)
        assertEquals(a.channelId, CompletionProfile.from(UserPreferencesCodec.decode(UserPreferencesCodec.encode(value))).channelId)
        assertEquals(75, a.channelId.length)
    }
    @Test fun allSoundVibrationProfilesHaveDistinctChannels() {
        val profiles = CompletionSound.entries.flatMap { sound -> CompletionVibration.entries.map {
            CompletionProfile.from(UserPreferences(sound, if (sound == CompletionSound.CUSTOM) "content://media/internal/audio/media/1" else null, it))
        } }
        assertEquals(12, profiles.map { it.channelId }.toSet().size)
        assertNotEquals(CompletionProfile.from(UserPreferences(CompletionSound.CUSTOM, "content://media/internal/audio/media/1")).channelId,
            CompletionProfile.from(UserPreferences(CompletionSound.CUSTOM, "content://media/internal/audio/media/2")).channelId)
    }
    @Test fun pickerCancelIsNoChangeAndConfirmedSilentDefaultCustomPreserveOtherChoices() {
        val original = UserPreferences(vibration = CompletionVibration.DOUBLE, keepScreenAwake = true)
        assertEquals(original, applySoundSelection(original, false, null))
        assertEquals(original, applySoundSelection(original, false, "content://media/internal/audio/media/8"))
        val silent = applySoundSelection(original, true, null)
        assertEquals(original.copy(sound = CompletionSound.SILENT), silent)
        assertEquals(original, applySoundSelection(silent, true, "content://settings/system/notification_sound", isDefault = true))
        val custom = applySoundSelection(original, true, "content://media/internal/audio/media/8")
        assertEquals(original.copy(sound = CompletionSound.CUSTOM, soundUri = "content://media/internal/audio/media/8"), custom)
        assertEquals(original, applySoundSelection(custom, true, "https://invalid"))
    }
    @Test fun vibrationPatternsAreMeaningful() {
        assertEquals(emptyList<Long>(), CompletionProfile.from(UserPreferences(vibration = CompletionVibration.OFF)).vibrationPattern)
        assertEquals(listOf(0L, 180L), CompletionProfile.from(UserPreferences(vibration = CompletionVibration.SHORT)).vibrationPattern)
        assertEquals(listOf(0L, 180L, 140L, 180L), CompletionProfile.from(UserPreferences(vibration = CompletionVibration.DOUBLE)).vibrationPattern)
    }
    @Test fun screenAwakeOnlyForForegroundRunningFocusWithOptIn() {
        for (mode in TimerMode.entries) for (status in TimerStatus.entries) for (foreground in listOf(false, true)) for (enabled in listOf(false, true)) {
            assertEquals(enabled && foreground && mode == TimerMode.FOCUS && status == TimerStatus.RUNNING,
                shouldKeepScreenAwake(UserPreferences(keepScreenAwake = enabled), TimerSnapshot(mode = mode, status = status,
                    remainingMs = if (status == TimerStatus.FINISHED) 0 else TimerSettings().durationMs(mode),
                    nextMode = if (status == TimerStatus.FINISHED) mode else null,
                    deadlineElapsedMs = if (status == TimerStatus.RUNNING) 100_000_000L else null,
                    deadlineWallMs = if (status == TimerStatus.RUNNING) 100_000_000L else null), foreground))
        }
    }
}
