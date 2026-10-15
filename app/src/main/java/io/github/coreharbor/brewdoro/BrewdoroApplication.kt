// Copyright (c) 2026 Brewdoro contributors. SPDX-License-Identifier: MIT
package io.github.coreharbor.brewdoro

import android.app.Application
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import io.github.coreharbor.brewdoro.domain.*
import io.github.coreharbor.brewdoro.platform.AlarmScheduler
import io.github.coreharbor.brewdoro.platform.CompletionNotifications
import java.io.File
import java.io.IOException
import java.util.UUID

class BrewdoroApplication : Application() {
    val repository: TimerRepository by lazy {
        TimerRepository(PreferencesSnapshotStore(this), clock, object : TimerEffects {
            override fun completed(mode: TimerMode) { notifications.show(mode) }
            override fun dismissCompletion() { notifications.cancel() }
            override fun synchronize(snapshot: TimerSnapshot, force: Boolean) {
                alarms.synchronize(snapshot, force)
            }
        })
    }
    val clock: TimerClock by lazy { AndroidTimerClock(this) }
    val alarms: AlarmScheduler by lazy { AlarmScheduler(this) }
    val notifications: CompletionNotifications by lazy { CompletionNotifications(this) }
}

private class PreferencesSnapshotStore(context: Context) : SnapshotStore {
    private val preferences = context.getSharedPreferences("timer", Context.MODE_PRIVATE)
    override fun load(): TimerSnapshot? {
        val raw = try { preferences.getString("snapshot", null) } catch (_: ClassCastException) { null }
        val decoded = SnapshotCodec.decode(raw)
        if (raw != null && decoded == null) Log.w("Brewdoro", "Invalid local snapshot; using safe defaults")
        return decoded
    }
    override fun save(snapshot: TimerSnapshot) {
        if (!preferences.edit().putString("snapshot", SnapshotCodec.encode(snapshot)).commit()) {
            throw IOException("Snapshot commit failed")
        }
    }
}

private class AndroidTimerClock(context: Context) : TimerClock {
    // BOOT_COUNT is stable across process deaths and increments on reboot (API 24+).
    // A process-only identity is safer than assuming an unknown boot is the same boot.
    private val boot = runCatching {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT).let { "count:$it" }
    }.getOrNull() ?: runCatching {
        File("/proc/sys/kernel/random/boot_id").readText().trim().takeIf { it.isNotEmpty() }
    }.getOrNull() ?: "process:" + UUID.randomUUID().toString()

    override fun now() = ClockReading(System.currentTimeMillis().coerceAtLeast(0L), SystemClock.elapsedRealtime(), boot)
}
