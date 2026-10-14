// Copyright (c) 2026 Brewdoro contributors. SPDX-License-Identifier: MIT
package io.github.coreharbor.brewdoro.platform

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import io.github.coreharbor.brewdoro.domain.TimerSnapshot
import io.github.coreharbor.brewdoro.domain.TimerStatus

class AlarmScheduler(private val context: Context) {
    private val manager = context.getSystemService(AlarmManager::class.java)
    private data class Key(val generation: String, val deadline: Long, val exact: Boolean)
    private var scheduled: Key? = null

    fun exactAllowed(): Boolean = Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()

    // Called only under TimerRepository's lock. ELAPSED_REALTIME_WAKEUP includes device sleep.
    fun synchronize(snapshot: TimerSnapshot, force: Boolean) {
        val key = if (snapshot.status == TimerStatus.RUNNING)
            Key(snapshot.generation, snapshot.deadlineElapsedMs!!, exactAllowed()) else null
        if (!force && key == scheduled) return
        scheduled?.let { cancel(it.generation) }
        scheduled = null
        if (key == null) return
        val pending = pendingIntent(key.generation)
        try {
            if (key.exact) {
                try {
                    manager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, key.deadline, pending)
                } catch (_: SecurityException) {
                    // Access can be revoked between the capability check and scheduling.
                    manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, key.deadline, pending)
                }
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, key.deadline, pending)
            }
            scheduled = key
        } catch (e: RuntimeException) {
            // Keep no success cache: next foreground/receiver reconciliation retries scheduling.
            Log.w("Brewdoro", "Alarm scheduling unavailable", e)
        }
    }

    private fun cancel(generation: String) {
        val existing = PendingIntent.getBroadcast(context, 0, intent(generation),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE) ?: return
        manager.cancel(existing)
        existing.cancel()
    }

    private fun pendingIntent(generation: String): PendingIntent = PendingIntent.getBroadcast(
        context, 0, intent(generation), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun intent(generation: String) = Intent(context, CompletionReceiver::class.java).apply {
        action = ACTION_COMPLETE
        data = Uri.Builder().scheme("brewdoro").authority("alarm").appendPath(generation).build()
        putExtra(EXTRA_GENERATION, generation)
    }

    companion object {
        const val ACTION_COMPLETE = "io.github.coreharbor.brewdoro.COMPLETE"
        const val EXTRA_GENERATION = "generation"
    }
}
