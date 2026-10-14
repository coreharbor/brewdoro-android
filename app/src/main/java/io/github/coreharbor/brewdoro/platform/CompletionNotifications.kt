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
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.coreharbor.brewdoro.MainActivity
import io.github.coreharbor.brewdoro.R
import io.github.coreharbor.brewdoro.domain.TimerMode

class CompletionNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun ensureChannel() {
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID,
            context.getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = context.getString(R.string.notification_channel_description)
        })
    }

    fun enabled(): Boolean {
        return (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context,
            Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            manager.areNotificationsEnabled() && manager.getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    @SuppressLint("MissingPermission") // enabled() checks POST_NOTIFICATIONS, including revocation races below.
    fun show(mode: TimerMode) {
        ensureChannel()
        if (!enabled()) return
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_cup)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(context.getString(if (mode == TimerMode.FOCUS)
                R.string.notification_focus_done else R.string.notification_break_done))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
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
