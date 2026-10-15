// Copyright (c) 2026 Brewdoro contributors. SPDX-License-Identifier: MIT
package io.github.coreharbor.brewdoro

import android.app.Activity
import android.media.RingtoneManager
import android.view.WindowManager
import io.github.coreharbor.brewdoro.domain.*
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
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
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
    private var preferences by mutableStateOf(UserPreferences())
    private var soundLabel by mutableStateOf<String?>(null)
    private var foreground by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)
    // Also serialize result publication, not just repository writes, to prevent stale UI snapshots.
    private val operations = Mutex()
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        update { app.repository.refresh(forceAlarm = true) }
    }

    private val soundPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                @Suppress("DEPRECATION")
                val uri = data.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                savePreferences(applySoundSelection(preferences, confirmed = true, uri = uri?.toString(),
                    isDefault = uri != null && RingtoneManager.isDefault(uri)))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        preferences = app.userPreferences.load()
        soundLabel = selectedSoundLabel()
        app.notifications.ensureChannel()
        view = app.repository.peek()
        setContent {
            view?.let { current ->
                val renderedPreferences = preferences
                BrewdoroApp(
                    state = current.snapshot,
                    preferences = renderedPreferences,
                    soundLabel = soundLabel,
                    onPreferences = { savePreferences(it, renderedPreferences) },
                    onPickSound = ::pickSound,
                    motionActive = foreground,
                    remainingMs = current.remainingMs,
                    notificationEnabled = notificationEnabled,
                    exactAlarmsEnabled = exactEnabled,
                    onStart = { update { app.repository.start() } },
                    onPause = { update { app.repository.pause(current.snapshot.generation) } },
                    onResetTimer = { update { app.repository.resetTimer() } },
                    onResetCycle = { update { app.repository.resetCycle() } },
                    onMode = { mode -> update { app.repository.selectMode(mode) } },
                    onSettings = { settings -> update { app.repository.updateSettings(settings) } },
                    onNotifications = ::requestNotifications,
                    onExactAlarms = ::requestExactAlarms,
                    error = error,
                    onLanguage = if (Build.VERSION.SDK_INT >= 33) ::requestLanguage else null,
                )
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                var first = true
                while (true) {
                    perform(refreshPermissions = first) { app.repository.refresh(forceAlarm = first) }
                    first = false
                    delay(1000)
                }
            }
        }
    }

    private fun update(operation: () -> TimerView) {
        lifecycleScope.launch { perform(clearError = true, refreshPermissions = true, operation = operation) }
    }

    private suspend fun perform(clearError: Boolean = false, refreshPermissions: Boolean = false, operation: () -> TimerView) {
        operations.withLock {
            try {
                view = withContext(Dispatchers.IO) { operation() }
                if (clearError) error = null
            } catch (e: java.io.IOException) {
                error = getString(R.string.persistence_error)
            }
            applyScreenAwake()
            if (refreshPermissions) {
                notificationEnabled = app.notifications.enabled()
                exactEnabled = app.alarms.exactAllowed()
            }
        }
    }

    private fun savePreferences(value: UserPreferences, baseline: UserPreferences = preferences) {
        // These tiny independent preferences commit before returning from the user action, so
        // immediate rotation/picker return cannot cancel an Activity-owned asynchronous save.
        // Merge only changed controls if another click came from the previous rendered frame.
        val merged = preferences.copy(
            sound = if (value.sound != baseline.sound || value.soundUri != baseline.soundUri) value.sound else preferences.sound,
            soundUri = if (value.sound != baseline.sound || value.soundUri != baseline.soundUri) value.soundUri else preferences.soundUri,
            vibration = if (value.vibration != baseline.vibration) value.vibration else preferences.vibration,
            keepScreenAwake = if (value.keepScreenAwake != baseline.keepScreenAwake) value.keepScreenAwake else preferences.keepScreenAwake,
        ).normalized()
        try {
            app.userPreferences.save(merged)
            preferences = merged
            soundLabel = selectedSoundLabel()
            app.notifications.ensureChannel()
            notificationEnabled = app.notifications.enabled()
            error = null
            applyScreenAwake()
        } catch (_: java.io.IOException) {
            error = getString(R.string.persistence_error)
        }
    }

    private fun selectedSoundLabel(): String = when (preferences.sound) {
        CompletionSound.DEFAULT -> getString(R.string.sound_default)
        CompletionSound.SILENT -> getString(R.string.sound_silent)
        CompletionSound.CUSTOM -> {
            if (app.notifications.selectedProfile().sound != CompletionSound.CUSTOM) getString(R.string.sound_unavailable)
            else runCatching {
                RingtoneManager.getRingtone(this, Uri.parse(preferences.soundUri))?.getTitle(this)
            }.getOrNull() ?: getString(R.string.sound_selected)
        }
    }

    private fun pickSound() {
        val selected = when (preferences.sound) {
            CompletionSound.DEFAULT -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            CompletionSound.SILENT -> null
            CompletionSound.CUSTOM -> Uri.parse(preferences.soundUri)
        }
        try {
            soundPicker.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, selected)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.completion_sound))
            })
        } catch (_: ActivityNotFoundException) {
            error = getString(R.string.sound_picker_unavailable)
        } catch (_: SecurityException) {
            error = getString(R.string.sound_picker_unavailable)
        }
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        preferences = app.userPreferences.load()
        soundLabel = selectedSoundLabel()
        // Never restore a screen flag from an old running view before reconciling a deadline.
        update { app.repository.refresh() }
    }

    override fun onPause() {
        foreground = false
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onPause()
    }

    override fun onDestroy() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }

    private fun applyScreenAwake() {
        val keep = view?.snapshot?.let { shouldKeepScreenAwake(preferences, it, foreground) } == true
        if (keep) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
        val granted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this,
            Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        openSettings(if (granted) Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, app.notifications.ensureChannel())
        else Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
    }

    private fun requestExactAlarms() {
        if (Build.VERSION.SDK_INT >= 31) {
            openSettings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
        } else {
            openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }

    private fun requestLanguage() {
        if (Build.VERSION.SDK_INT >= 33) {
            openSettings(Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.parse("package:$packageName")))
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
