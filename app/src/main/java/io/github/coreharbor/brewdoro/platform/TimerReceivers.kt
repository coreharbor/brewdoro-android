// Copyright (c) 2026 Brewdoro contributors. SPDX-License-Identifier: MIT
package io.github.coreharbor.brewdoro.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.coreharbor.brewdoro.BrewdoroApplication
import java.util.concurrent.Executors

/** One short IO task, no service or activity launch. Same process/repository as the UI. */
private object ReceiverWork {
    private val executor = Executors.newSingleThreadExecutor()
    fun submit(receiver: BroadcastReceiver, context: Context, block: (BrewdoroApplication) -> Unit) {
        val pending = receiver.goAsync()
        executor.execute {
            try {
                block(context.applicationContext as BrewdoroApplication)
            } catch (e: Exception) {
                // A failed persistence write must not emit a completion or count it in memory.
                Log.e("Brewdoro", "Timer receiver could not persist/reconcile state", e)
            } finally {
                pending.finish()
            }
        }
    }
}

class CompletionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_COMPLETE) return
        val generation = intent.getStringExtra(AlarmScheduler.EXTRA_GENERATION) ?: return
        ReceiverWork.submit(this, context) { it.repository.alarm(generation) }
    }
}

class RestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        ReceiverWork.submit(this, context) { it.repository.refresh(forceAlarm = true) }
    }
    companion object {
        private const val EXACT_ACCESS_CHANGED = "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
        private val ACTIONS = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED, EXACT_ACCESS_CHANGED)
    }
}
