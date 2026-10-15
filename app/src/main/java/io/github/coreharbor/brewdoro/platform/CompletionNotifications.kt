// Copyright (c) 2026 Brewdoro contributors. SPDX-License-Identifier: MIT
package io.github.coreharbor.brewdoro.platform

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import io.github.coreharbor.brewdoro.domain.*
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.coreharbor.brewdoro.MainActivity
import io.github.coreharbor.brewdoro.R
import io.github.coreharbor.brewdoro.domain.TimerMode

class CompletionNotifications(private val context: Context, private val preferences: () -> UserPreferences = { UserPreferences() }) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    /** Missing/revoked custom media falls back without ever playing audio directly. */
    fun selectedProfile(): CompletionProfile {
        val requested = CompletionProfile.from(preferences())
        if (requested.sound != CompletionSound.CUSTOM) return requested
        val accessible = runCatching {
            context.contentResolver.openAssetFileDescriptor(Uri.parse(requested.soundUri), "r")?.use { true } ?: false
        }.getOrDefault(false)
        return if (accessible) requested else requested.copy(sound = CompletionSound.DEFAULT, soundUri = null)
    }

    fun ensureChannel(): String = ensureChannel(selectedProfile())

    private fun ensureChannel(profile: CompletionProfile): String {
        val id = profile.channelId
        // Do not update or recreate existing channels: system/user settings always win.
        if (manager.getNotificationChannel(id) == null) {
            manager.createNotificationChannel(NotificationChannel(id,
                context.getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.notification_channel_description)
                val sound = when (profile.sound) {
                    CompletionSound.DEFAULT -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    CompletionSound.SILENT -> null
                    CompletionSound.CUSTOM -> Uri.parse(profile.soundUri)
                }
                setSound(sound, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                profile.vibrationPattern?.let { pattern ->
                    enableVibration(pattern.isNotEmpty())
                    if (pattern.isNotEmpty()) vibrationPattern = pattern.toLongArray()
                }
            })
        }
        return id
    }

    fun enabled(): Boolean = enabled(ensureChannel())
    private fun enabled(channelId: String): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context,
            Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            manager.areNotificationsEnabled() &&
            manager.getNotificationChannel(channelId)?.importance != NotificationManager.IMPORTANCE_NONE

    @SuppressLint("MissingPermission") // enabled() checks POST_NOTIFICATIONS, including revocation races below.
    fun show(mode: TimerMode) {
        val channelId = ensureChannel()
        if (!enabled(channelId)) return
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_cup)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(context.getString(if (mode == TimerMode.FOCUS)
                R.string.notification_focus_done else R.string.notification_break_done))
            .setContentIntent(open)
            .setAutoCancel(true)
            // Each call is a new, persisted phase completion, not a progress update.
            .setOnlyAlertOnce(false)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.w("Brewdoro", "Notification permission changed", e)
        }
    }

    fun cancel() { manager.cancel(NOTIFICATION_ID) }

    companion object {
        const val CHANNEL_ID = "session_completion"
        private const val NOTIFICATION_ID = 1
    }
}
