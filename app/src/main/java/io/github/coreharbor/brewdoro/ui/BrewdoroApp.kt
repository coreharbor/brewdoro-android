// Copyright (c) 2026 Brewdoro contributors. SPDX-License-Identifier: MIT
package io.github.coreharbor.brewdoro.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.coreharbor.brewdoro.R
import io.github.coreharbor.brewdoro.domain.TimerMode
import io.github.coreharbor.brewdoro.domain.coffeeFillFraction
import io.github.coreharbor.brewdoro.domain.TimerSettings
import io.github.coreharbor.brewdoro.domain.TimerSnapshot
import io.github.coreharbor.brewdoro.domain.TimerStatus

/**
 * Stateless timer UI: all timer transitions are delegated to the caller.
 * Only navigation, settings drafts, and confirmation visibility are local UI state.
 */
@Composable
fun BrewdoroApp(
    state: TimerSnapshot,
    remainingMs: Long,
    notificationEnabled: Boolean,
    exactAlarmsEnabled: Boolean,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResetTimer: () -> Unit,
    onResetCycle: () -> Unit,
    onMode: (TimerMode) -> Unit,
    onSettings: (TimerSettings) -> Unit,
    onNotifications: () -> Unit,
    onExactAlarms: () -> Unit,
    error: String? = null,
) {
    val canEditSettings = state.status != TimerStatus.RUNNING && state.status != TimerStatus.PAUSED
    var settingsVisible by rememberSaveable { mutableStateOf(false) }
    var confirmCycleReset by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = settingsVisible) { settingsVisible = false }
    BrewdoroTheme {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding(),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 600.dp).fillMaxSize()
                        .verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (error != null) ErrorCard(error)
                    if (settingsVisible) {
                        SettingsScreen(
                            settings = state.settings,
                            enabled = canEditSettings,
                            onCancel = { settingsVisible = false },
                            onSave = { onSettings(it); settingsVisible = false },
                        )
                    } else {
                        Text(
                            stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineLarge,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            stringResource(R.string.app_tagline),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        TextButton(onClick = { settingsVisible = true }, enabled = canEditSettings) {
                            Text(stringResource(R.string.settings_title))
                        }
                        if (!canEditSettings) {
                            Text(stringResource(R.string.settings_locked), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        }
                        TimerPanel(state, remainingMs)
                        if (state.status == TimerStatus.FINISHED) {
                            InfoCard(
                                stringResource(R.string.session_finished),
                                stringResource(R.string.next_session, modeLabel(state.nextMode ?: state.mode)),
                            )
                        }
                        Button(
                            onClick = if (state.status == TimerStatus.RUNNING) onPause else onStart,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                            contentPadding = PaddingValues(16.dp),
                        ) {
                            Text(
                                when (state.status) {
                                    TimerStatus.RUNNING -> stringResource(R.string.pause)
                                    TimerStatus.PAUSED -> stringResource(R.string.resume)
                                    TimerStatus.FINISHED -> stringResource(
                                        R.string.start_next, modeLabel(state.nextMode ?: state.mode),
                                    )
                                    TimerStatus.IDLE -> stringResource(R.string.start)
                                },
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center,
                            )
                        }
                        OutlinedButton(
                            onClick = onResetTimer,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) { Text(stringResource(R.string.reset_timer), textAlign = TextAlign.Center) }
                        Text(
                            stringResource(R.string.reset_timer_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ModePicker(state, onMode)
                        Text(
                            stringResource(R.string.completed_focus, state.completedFocus),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            stringResource(R.string.cycle_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        TextButton(onClick = { confirmCycleReset = true }) {
                            Text(stringResource(R.string.reset_cycle), textAlign = TextAlign.Center)
                        }
                        PermissionCard(
                            notificationEnabled, exactAlarmsEnabled, onNotifications, onExactAlarms,
                        )
                    }
                }
            }
            if (confirmCycleReset) {
                AlertDialog(
                    onDismissRequest = { confirmCycleReset = false },
                    title = { Text(stringResource(R.string.reset_cycle_title)) },
                    text = {
                        Text(
                            stringResource(R.string.reset_cycle_warning),
                            modifier = Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { confirmCycleReset = false; onResetCycle() }) {
                            Text(stringResource(R.string.reset_cycle))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmCycleReset = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun TimerPanel(state: TimerSnapshot, remainingMs: Long) {
    val remaining = remainingMs.coerceIn(0L, state.durationMs.coerceAtLeast(0L))
    val secondsTotal = remaining / 1000L + if (remaining % 1000L > 0) 1L else 0L
    val minutes = (secondsTotal / 60L).toInt()
    val seconds = (secondsTotal % 60L).toInt()
    val accessibleTime = stringResource(
        R.string.timer_description,
        pluralStringResource(R.plurals.minutes, minutes, minutes),
        pluralStringResource(R.plurals.seconds, seconds, seconds),
    )
    Text(modeLabel(state.mode), style = MaterialTheme.typography.titleLarge)
    CoffeeCup(
        fractionRemaining = coffeeFillFraction(state.mode, remaining, state.durationMs),
        running = state.status == TimerStatus.RUNNING,
    )
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // Fit even 180:00 on narrow screens at large system font sizes; other text can wrap.
        val timerSize = (maxWidth.value / (6f * 0.66f * fontScale)).coerceAtMost(64f).sp
        Text(
            stringResource(R.string.timer_digits, minutes, seconds),
            fontSize = timerSize,
            fontWeight = FontWeight.Light,
            maxLines = 1,
            modifier = Modifier.clearAndSetSemantics { contentDescription = accessibleTime },
        )
    }
    Text(
        stringResource(
            when (state.status) {
                TimerStatus.IDLE -> R.string.status_idle
                TimerStatus.RUNNING -> R.string.status_running
                TimerStatus.PAUSED -> R.string.status_paused
                TimerStatus.FINISHED -> R.string.status_finished
            },
        ),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun ModePicker(state: TimerSnapshot, onMode: (TimerMode) -> Unit) {
    val canChange = state.status != TimerStatus.RUNNING && state.status != TimerStatus.PAUSED
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.mode_title), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            for (mode in listOf(TimerMode.FOCUS, TimerMode.SHORT_BREAK, TimerMode.LONG_BREAK)) {
                OutlinedButton(
                    onClick = { onMode(mode) },
                    enabled = canChange,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .semantics { selected = state.mode == mode },
                ) {
                    Text(
                        stringResource(
                            if (mode == state.mode) R.string.mode_selected_duration else R.string.mode_duration,
                            modeLabel(mode), (state.settings.durationMs(mode) / 60000L).toInt(),
                        ),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            if (!canChange) {
                Text(stringResource(R.string.mode_locked), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SettingsScreen(settings: TimerSettings, enabled: Boolean, onCancel: () -> Unit, onSave: (TimerSettings) -> Unit) {
    var focus by rememberSaveable(settings.focusMinutes) { mutableStateOf(settings.focusMinutes.toString()) }
    var shortBreak by rememberSaveable(settings.shortBreakMinutes) {
        mutableStateOf(settings.shortBreakMinutes.toString())
    }
    var longBreak by rememberSaveable(settings.longBreakMinutes) {
        mutableStateOf(settings.longBreakMinutes.toString())
    }
    val focusValue = focus.toIntOrNull()
    val shortValue = shortBreak.toIntOrNull()
    val longValue = longBreak.toIntOrNull()
    val valid = validMinutes(focusValue) && validMinutes(shortValue) && validMinutes(longValue)
    Text(
        stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.semantics { heading() },
    )
    Text(stringResource(if (enabled) R.string.settings_future_only else R.string.settings_locked), style = MaterialTheme.typography.bodyLarge)
    DurationField(stringResource(R.string.focus_duration), focus, enabled) { focus = it }
    DurationField(stringResource(R.string.short_break_duration), shortBreak, enabled) { shortBreak = it }
    DurationField(stringResource(R.string.long_break_duration), longBreak, enabled) { longBreak = it }
    Button(
        onClick = {
            // Revalidate at the boundary rather than trusting only the disabled button.
            if (enabled && valid) onSave(TimerSettings(focusValue!!, shortValue!!, longValue!!))
        },
        enabled = enabled && valid,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) { Text(stringResource(R.string.save_settings)) }
    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.cancel))
    }
}

private fun validMinutes(value: Int?): Boolean = value != null && value in 1..180

@Composable
private fun DurationField(label: String, value: String, enabled: Boolean, onValue: (String) -> Unit) {
    val valid = validMinutes(value.toIntOrNull())
    OutlinedTextField(
        value = value,
        enabled = enabled,
        onValueChange = { candidate ->
            if (candidate.length <= 3 && candidate.all { it in '0'..'9' }) onValue(candidate)
        },
        label = { Text(label) },
        supportingText = { Text(stringResource(if (valid) R.string.duration_range else R.string.duration_error)) },
        isError = !valid,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PermissionCard(
    notifications: Boolean,
    exactAlarms: Boolean,
    onNotifications: () -> Unit,
    onExactAlarms: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.alerts_title), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(if (notifications) R.string.notifications_enabled else R.string.notifications_disabled))
            if (!notifications) {
                OutlinedButton(onClick = onNotifications, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.enable_notifications), textAlign = TextAlign.Center)
                }
            }
            Text(stringResource(if (exactAlarms) R.string.exact_alarms_enabled else R.string.exact_alarms_disabled))
            if (!exactAlarms) {
                OutlinedButton(onClick = onExactAlarms, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.enable_exact_alarms), textAlign = TextAlign.Center)
                }
            }
            Text(
                stringResource(R.string.background_limitations),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                title, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite },
            )
            Text(body)
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.error_title), style = MaterialTheme.typography.titleMedium)
            Text(message)
        }
    }
}

@Composable
private fun modeLabel(mode: TimerMode): String = stringResource(
    when (mode) {
        TimerMode.FOCUS -> R.string.mode_focus
        TimerMode.SHORT_BREAK -> R.string.mode_short_break
        TimerMode.LONG_BREAK -> R.string.mode_long_break
    },
)
