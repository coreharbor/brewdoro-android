// Copyright (c) 2026 Brewdoro contributors. SPDX-License-Identifier: MIT
package io.github.coreharbor.brewdoro.domain

import java.security.MessageDigest

enum class CompletionSound { DEFAULT, SILENT, CUSTOM }
/** SYSTEM preserves the pre-0.1.4 channel behaviour until the user makes a choice. */
enum class CompletionVibration { SYSTEM, OFF, SHORT, DOUBLE }

data class UserPreferences(
    val sound: CompletionSound = CompletionSound.DEFAULT,
    val soundUri: String? = null,
    val vibration: CompletionVibration = CompletionVibration.SYSTEM,
    val keepScreenAwake: Boolean = false,
) {
    fun normalized(): UserPreferences = if (sound == CompletionSound.CUSTOM && !validToneUri(soundUri))
        copy(sound = CompletionSound.DEFAULT, soundUri = null)
    else if (sound != CompletionSound.CUSTOM) copy(soundUri = null) else this
}

// System picker supplies local content URIs. Never accept remote/file URLs or request storage access.
fun validToneUri(uri: String?): Boolean = uri != null && uri.length <= 4096 &&
    uri.startsWith("content://") && uri.length > "content://".length && !uri.contains('\n') && !uri.contains('\r')

object UserPreferencesCodec {
    fun encode(value: UserPreferences): String = value.normalized().let {
        listOf("1", it.sound.name, it.vibration.name, it.keepScreenAwake.toString(), it.soundUri.orEmpty()).joinToString("\n")
    }
    fun decode(raw: String?): UserPreferences {
        val parts = raw?.split('\n', limit = 5) ?: return UserPreferences()
        if (parts.size != 5 || parts[0] != "1") return UserPreferences()
        return UserPreferences(
            sound = CompletionSound.entries.firstOrNull { it.name == parts[1] } ?: CompletionSound.DEFAULT,
            soundUri = parts[4].takeIf { it.isNotEmpty() },
            vibration = CompletionVibration.entries.firstOrNull { it.name == parts[2] } ?: CompletionVibration.SYSTEM,
            keepScreenAwake = parts[3] == "true",
        ).normalized()
    }
}

/** Cancelled/missing picker results are no-ops; null confirmed URI means explicit silence. */
fun applySoundSelection(current: UserPreferences, confirmed: Boolean, uri: String?, isDefault: Boolean = false): UserPreferences {
    if (!confirmed) return current
    return when {
        uri == null -> current.copy(sound = CompletionSound.SILENT, soundUri = null)
        isDefault -> current.copy(sound = CompletionSound.DEFAULT, soundUri = null)
        else -> current.copy(sound = CompletionSound.CUSTOM, soundUri = uri).normalized()
    }
}

/** Pure channel contract. Hash only sound/vibration, never unrelated UI preferences. */
data class CompletionProfile(val sound: CompletionSound, val soundUri: String?, val vibration: CompletionVibration) {
    val channelId: String get() {
        if (this == from(UserPreferences())) return "session_completion"
        val key = "v1|" + sound.name + "|" + soundUri.orEmpty() + "|" + vibration.name
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        return "completion_" + digest.joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    val vibrationPattern: List<Long>? get() = when (vibration) {
        CompletionVibration.SYSTEM -> null
        CompletionVibration.OFF -> emptyList()
        CompletionVibration.SHORT -> listOf(0L, 180L)
        CompletionVibration.DOUBLE -> listOf(0L, 180L, 140L, 180L)
    }
    companion object {
        fun from(preferences: UserPreferences): CompletionProfile = preferences.normalized().let {
            CompletionProfile(it.sound, it.soundUri, it.vibration)
        }
    }
}

fun shouldKeepScreenAwake(preferences: UserPreferences, snapshot: TimerSnapshot, foreground: Boolean): Boolean =
    preferences.keepScreenAwake && foreground && snapshot.status == TimerStatus.RUNNING && snapshot.mode == TimerMode.FOCUS
