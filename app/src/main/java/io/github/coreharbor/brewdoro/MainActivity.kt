// Copyright (c) 2026 Brewdoro contributors. SPDX-License-Identifier: MIT
package io.github.coreharbor.brewdoro

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.coreharbor.brewdoro.domain.TimerView
import io.github.coreharbor.brewdoro.ui.BrewdoroApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val app get() = application as BrewdoroApplication
    private var view by mutableStateOf<TimerView?>(null)
    private var notificationEnabled by mutableStateOf(false)
    private var exactEnabled by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)
    // Also serialize result publication, not just repository writes, to prevent stale UI snapshots.
    private val operations = Mutex()
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        update { app.repository.refresh(forceAlarm = true) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app.notifications.ensureChannel()
        view = app.repository.peek()
        setContent {
            view?.let { current ->
                BrewdoroApp(
                    state = current.snapshot,
                    remainingMs = current.remainingMs,
                    notificationEnabled = notificationEnabled,
                    exactAlarmsEnabled = exactEnabled,
                    onStart = { update { app.repository.start() } },
                    onPause = { update { app.repository.pause() } },
                    onResetTimer = { update { app.repository.resetTimer() } },
                    onResetCycle = { update { app.repository.resetCycle() } },
                    onMode = { mode -> update { app.repository.selectMode(mode) } },
                    onSettings = { settings -> update { app.repository.updateSettings(settings) } },
                    onNotifications = ::requestNotifications,
                    onExactAlarms = ::requestExactAlarms,
                    error = error,
                )
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                var first = true
                while (true) {
                    perform { app.repository.refresh(forceAlarm = first) }
                    first = false
                    delay(250)
                }
            }
        }
    }

    private fun update(operation: () -> TimerView) {
        lifecycleScope.launch { perform(clearError = true, operation = operation) }
    }

    private suspend fun perform(clearError: Boolean = false, operation: () -> TimerView) {
        operations.withLock {
            try {
                view = withContext(Dispatchers.IO) { operation() }
                if (clearError) error = null
            } catch (e: java.io.IOException) {
                error = getString(R.string.persistence_error)
            }
            notificationEnabled = app.notifications.enabled()
            exactEnabled = app.alarms.exactAllowed()
        }
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            val preferences = getSharedPreferences("permissions", MODE_PRIVATE)
            val asked = preferences.getBoolean("notificationAsked", false)
            if (!asked || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                preferences.edit().putBoolean("notificationAsked", true).apply()
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
    }

    private fun requestExactAlarms() {
        if (Build.VERSION.SDK_INT >= 31) {
            openSettings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
        }
    }

    private fun openSettings(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            } catch (_: ActivityNotFoundException) {
                // Nonstandard devices may have no settings activity; keep the timer usable.
            }
        }
    }
}
