// Copyright (c) 2026 Brewdoro contributors. SPDX-License-Identifier: MIT
package io.github.coreharbor.brewdoro.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.semantics.LiveRegionMode
import io.github.coreharbor.brewdoro.domain.UserPreferences
import io.github.coreharbor.brewdoro.domain.CompletionVibration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.coreharbor.brewdoro.R
import io.github.coreharbor.brewdoro.domain.TimerMode
import io.github.coreharbor.brewdoro.domain.TimerSettings
import io.github.coreharbor.brewdoro.domain.TimerSnapshot
import io.github.coreharbor.brewdoro.domain.TimerStatus
import io.github.coreharbor.brewdoro.domain.coffeeFillFraction

/** Stateless timer callbacks; only navigation, drafts and confirmation live in the UI. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
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
    onLanguage: (() -> Unit)? = null,
    preferences: UserPreferences = UserPreferences(),
    soundLabel: String? = null,
    onPreferences: (UserPreferences) -> Unit = {},
    onPickSound: () -> Unit = {},
    motionActive: Boolean = false,
) {
    val canEdit = state.status != TimerStatus.RUNNING && state.status != TimerStatus.PAUSED
    var settingsVisible by rememberSaveable { mutableStateOf(false) }
    var confirmCycleReset by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = settingsVisible && !confirmCycleReset) { settingsVisible = false }
    BrewdoroTheme {
        Surface(color = LinuxPalette.Background, modifier = Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
            Column(Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)).imePadding()) {
                Row(
                    Modifier.fillMaxWidth().background(LinuxPalette.Header)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (settingsVisible) {
                        Text(stringResource(R.string.settings_title), fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f).padding(start = 12.dp).semantics { heading() })
                        TextButton(onClick = { settingsVisible = false }, modifier = Modifier.testTag("close_settings")) {
                            Text(stringResource(R.string.done), color = LinuxPalette.Secondary)
                        }
                    } else {
                        IconButton(onClick = { settingsVisible = true }, modifier = Modifier.size(48.dp).testTag("open_settings")) {
                            Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.settings_title),
                                tint = LinuxPalette.Secondary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite })
                }
                if (settingsVisible) {
                    SettingsScreen(
                        settings = state.settings, enabled = canEdit,
                        preferences = preferences, soundLabel = soundLabel, onPreferences = onPreferences, onPickSound = onPickSound,
                        notifications = notificationEnabled, exactAlarms = exactAlarmsEnabled,
                        onNotifications = onNotifications, onExactAlarms = onExactAlarms, onLanguage = onLanguage,
                        onResetCycle = { confirmCycleReset = true },
                        onSave = { onSettings(it); settingsVisible = false },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    MainTimerScreen(state, remainingMs, onStart, onPause, onResetTimer, onMode,
                        modifier = Modifier.weight(1f), motionActive = motionActive)
                }
            }
            if (confirmCycleReset) {
                AlertDialog(
                    onDismissRequest = { confirmCycleReset = false },
                    title = { Text(stringResource(R.string.reset_cycle_title)) },
                    text = { Text(stringResource(R.string.reset_cycle_warning),
                        modifier = Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) },
                    confirmButton = {
                        TextButton(onClick = { confirmCycleReset = false; onResetCycle() }, modifier = Modifier.testTag("confirm_reset_cycle")) {
                            Text(stringResource(R.string.reset_cycle))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmCycleReset = false }, modifier = Modifier.testTag("cancel_reset_cycle")) {
                            Text(stringResource(R.string.cancel))
                        }
                    },
                    modifier = Modifier.testTag("cycle_confirmation"),
                )
            }
        }
    }
}

/** Switch on the inset-adjusted viewport, not device orientation (also works in split screen).
 * The scroll container has zero range when content fits, and is a fallback rather than a
 * requirement for ordinary landscape. Nothing gets scaled below its 48dp touch target.
 */
@Composable
private fun MainTimerScreen(
    state: TimerSnapshot, remainingMs: Long, onStart: () -> Unit, onPause: () -> Unit,
    onResetTimer: () -> Unit, onMode: (TimerMode) -> Unit, modifier: Modifier = Modifier, motionActive: Boolean = false,
) {
    BoxWithConstraints(modifier.fillMaxWidth().testTag("minimal_main")) {
        val viewportHeight = maxHeight
        // 520dp still fits 136dp cup + 24dp gap + 296dp controls + 40dp padding,
        // leaving room for landscape cutouts on a nominal 640dp-wide display.
        val sideBySide = maxWidth >= 520.dp && maxWidth > maxHeight
        Column(
            Modifier.fillMaxSize().testTag("main_scroll").verticalScroll(rememberScrollState())
                .heightIn(min = viewportHeight)
                .padding(horizontal = 20.dp, vertical = if (sideBySide) 8.dp else 0.dp)
                .then(if (sideBySide) Modifier else Modifier.padding(top = 16.dp, bottom = 32.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (sideBySide) {
                Row(
                    Modifier.widthIn(max = 640.dp).fillMaxWidth().testTag("compact_main"),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        CoffeeCup(coffeeFillFraction(state.mode, remainingMs, state.durationMs), animate = motionActive && state.status == TimerStatus.RUNNING)
                    }
                    Column(Modifier.width(296.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        TimerReadout(state, remainingMs, compact = true)
                        Spacer(Modifier.height(8.dp))
                        if (LocalDensity.current.fontScale > 1.2f) {
                            // At accessibility font sizes keep whole RU labels readable rather
                            // than breaking the last letter into a narrow side-by-side button.
                            PrimaryAction(state, onStart, onPause, Modifier.fillMaxWidth())
                            Spacer(Modifier.height(4.dp))
                            ResetAction(onResetTimer, Modifier.fillMaxWidth())
                        } else {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                PrimaryAction(state, onStart, onPause, Modifier.weight(1f))
                                ResetAction(onResetTimer, Modifier.weight(0.65f))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        ModePicker(state, onMode)
                    }
                }
            } else {
                // Keep the Linux-parity portrait layout, including all original spacing.
                Column(Modifier.widthIn(max = 296.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    TimerReadout(state, remainingMs)
                    Spacer(Modifier.height(18.dp))
                    CoffeeCup(coffeeFillFraction(state.mode, remainingMs, state.durationMs), animate = motionActive && state.status == TimerStatus.RUNNING)
                    Spacer(Modifier.height(26.dp))
                    PrimaryAction(state, onStart, onPause, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    ResetAction(onResetTimer)
                    Spacer(Modifier.height(28.dp))
                    ModePicker(state, onMode)
                }
            }
        }
    }
}

@Composable
private fun PrimaryAction(state: TimerSnapshot, onStart: () -> Unit, onPause: () -> Unit, modifier: Modifier) {
    val status = state.status
    OutlinedButton(
        onClick = if (status == TimerStatus.RUNNING) onPause else onStart,
        modifier = modifier.heightIn(min = 48.dp).testTag("primary_action"),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, LinuxPalette.AccentLight.copy(alpha = 0.45f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = LinuxPalette.Accent.copy(alpha = 0.28f), contentColor = LinuxPalette.Text),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(stringResource(when (status) {
            TimerStatus.RUNNING -> R.string.pause
            TimerStatus.PAUSED -> R.string.resume
            TimerStatus.FINISHED -> if (state.settings.cycleEnabled) R.string.start_next_short else R.string.start
            TimerStatus.IDLE -> R.string.start
        }), fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ResetAction(onResetTimer: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onResetTimer, modifier = modifier.heightIn(min = 48.dp).testTag("reset_timer")) {
        Text(stringResource(R.string.reset_short), fontSize = 13.sp, color = LinuxPalette.Muted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun TimerReadout(state: TimerSnapshot, remainingMs: Long, compact: Boolean = false) {
    val time = timerDisplay(remainingMs, state.durationMs)
    val accessibleTime = stringResource(R.string.timer_description,
        pluralStringResource(R.plurals.minutes, time.minutes, time.minutes),
        pluralStringResource(R.plurals.seconds, time.seconds, time.seconds))
    Text(modeLabel(state.mode).uppercase(), color = LinuxPalette.Secondary, fontSize = 12.sp,
        fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp, textAlign = TextAlign.Center,
        lineHeight = if (compact) 16.sp else androidx.compose.ui.unit.TextUnit.Unspecified,
        modifier = Modifier.testTag("mode_label"))
    Spacer(Modifier.height(if (compact) 2.dp else 4.dp))
    if (state.settings.cycleEnabled) {
        Text(stringResource(R.string.session_position, sessionPosition(state)), color = LinuxPalette.Muted,
            fontSize = 11.sp, lineHeight = if (compact) 16.sp else androidx.compose.ui.unit.TextUnit.Unspecified,
            modifier = Modifier.testTag("session_label"))
    }
    Spacer(Modifier.height(if (compact) 2.dp else 12.dp))
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // Normal phones use the exact Linux 68sp. Shrink only when large fonts/narrow space demand it.
        val digits = if (time.minutes >= 100) 6 else 5
        val timerSize = minOf(68f, maxWidth.value / (digits * 0.60f * fontScale)).sp
        Text(stringResource(R.string.timer_digits, time.minutes, time.seconds),
            style = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = timerSize,
                fontWeight = FontWeight.Light, letterSpacing = (-2).sp, fontFeatureSettings = "tnum"),
            color = LinuxPalette.Text, maxLines = 1,
            modifier = Modifier.testTag("timer_digits").clearAndSetSemantics { contentDescription = accessibleTime })
    }
}

@Composable
private fun ModePicker(state: TimerSnapshot, onMode: (TimerMode) -> Unit) {
    val enabled = state.status != TimerStatus.RUNNING && state.status != TimerStatus.PAUSED
    Row(Modifier.fillMaxWidth().testTag("duration_presets"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (mode in listOf(TimerMode.FOCUS, TimerMode.SHORT_BREAK, TimerMode.LONG_BREAK)) {
            val isSelected = state.mode == mode
            val description = stringResource(R.string.mode_duration, modeLabel(mode),
                (state.settings.durationMs(mode) / 60_000L).toInt())
            // Linux's 34dp visual sits inside a real 48dp target, without changing its silhouette.
            Box(Modifier.weight(1f).heightIn(min = 48.dp).testTag("preset_${mode.name}")
                .selectable(selected = isSelected, enabled = enabled, role = Role.RadioButton, onClick = { onMode(mode) })
                .semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.48f),
                    color = if (isSelected) LinuxPalette.Accent.copy(alpha = 0.28f) else LinuxPalette.Surface,
                    shape = RoundedCornerShape(10.dp),
                    border = if (isSelected) BorderStroke(1.dp, LinuxPalette.AccentLight.copy(alpha = 0.45f)) else null,
                ) {
                    Text(stringResource(R.string.preset_minutes, (state.settings.durationMs(mode) / 60_000L).toInt()),
                        modifier = Modifier.heightIn(min = 34.dp).padding(horizontal = 4.dp, vertical = 8.dp),
                        color = if (isSelected) LinuxPalette.Text else LinuxPalette.Secondary,
                        fontSize = 12.sp, lineHeight = 18.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    settings: TimerSettings, enabled: Boolean, notifications: Boolean, exactAlarms: Boolean,
    preferences: UserPreferences, soundLabel: String?, onPreferences: (UserPreferences) -> Unit, onPickSound: () -> Unit,
    onNotifications: () -> Unit, onExactAlarms: () -> Unit, onLanguage: (() -> Unit)?,
    onResetCycle: () -> Unit, onSave: (TimerSettings) -> Unit, modifier: Modifier = Modifier,
) {
    var focus by rememberSaveable(settings.focusMinutes) { mutableStateOf(settings.focusMinutes.toString()) }
    var shortBreak by rememberSaveable(settings.shortBreakMinutes) { mutableStateOf(settings.shortBreakMinutes.toString()) }
    var longBreak by rememberSaveable(settings.longBreakMinutes) { mutableStateOf(settings.longBreakMinutes.toString()) }
    var cycleEnabled by rememberSaveable(settings.cycleEnabled) { mutableStateOf(settings.cycleEnabled) }
    var autoStartEnabled by rememberSaveable(settings.autoStartEnabled) { mutableStateOf(settings.autoStartEnabled) }
    val focusValue = focus.toIntOrNull()
    val shortValue = shortBreak.toIntOrNull()
    val longValue = longBreak.toIntOrNull()
    val valid = validMinutes(focusValue) && validMinutes(shortValue) && validMinutes(longValue)
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).testTag("settings_screen").padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 420.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(if (enabled) R.string.settings_future_only else R.string.settings_locked),
                color = LinuxPalette.Secondary, style = MaterialTheme.typography.bodySmall)
            DurationField(stringResource(R.string.focus_duration), "duration_focus", focus, enabled) { focus = it }
            DurationField(stringResource(R.string.short_break_duration), "duration_short", shortBreak, enabled) { shortBreak = it }
            DurationField(stringResource(R.string.long_break_duration), "duration_long", longBreak, enabled) { longBreak = it }
            CycleSetting(stringResource(R.string.cycle_enabled), stringResource(R.string.cycle_description),
                "cycle_enabled", cycleEnabled, enabled) { cycleEnabled = it }
            CycleSetting(stringResource(R.string.auto_start_enabled), stringResource(R.string.auto_start_description),
                "auto_start_enabled", autoStartEnabled, enabled && cycleEnabled) { autoStartEnabled = it }
            OutlinedButton(onClick = {
                if (enabled && valid) onSave(settings.copy(focusMinutes = focusValue!!, shortBreakMinutes = shortValue!!,
                    longBreakMinutes = longValue!!, cycleEnabled = cycleEnabled, autoStartEnabled = autoStartEnabled))
            }, enabled = enabled && valid, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("save_durations")) {
                Text(stringResource(R.string.save_settings))
            }
            Text(stringResource(R.string.comfort_title), modifier = Modifier.semantics { heading() }, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.comfort_live), color = LinuxPalette.Muted, style = MaterialTheme.typography.bodySmall)
            CycleSetting(stringResource(R.string.keep_screen_awake), stringResource(R.string.keep_screen_description),
                "keep_screen_awake", preferences.keepScreenAwake, true) { onPreferences(preferences.copy(keepScreenAwake = it)) }
            OutlinedButton(onClick = onPickSound,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("completion_sound")) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.completion_sound))
                    Text(soundLabel ?: stringResource(R.string.sound_default), style = MaterialTheme.typography.bodySmall,
                        color = LinuxPalette.Secondary, textAlign = TextAlign.Center)
                }
            }
            Text(stringResource(R.string.completion_vibration), fontWeight = FontWeight.Bold)
            for (choice in CompletionVibration.entries) {
                val label = stringResource(when (choice) {
                    CompletionVibration.SYSTEM -> R.string.vibration_system
                    CompletionVibration.OFF -> R.string.vibration_off
                    CompletionVibration.SHORT -> R.string.vibration_short
                    CompletionVibration.DOUBLE -> R.string.vibration_double
                })
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("vibration_" + choice.name)
                    .selectable(selected = preferences.vibration == choice, role = Role.RadioButton,
                        onClick = { onPreferences(preferences.copy(vibration = choice)) })
                    .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.RadioButton(selected = preferences.vibration == choice, onClick = null)
                    Spacer(Modifier.width(12.dp))
                    Text(label)
                }
            }
            Text(stringResource(R.string.channel_overrides), color = LinuxPalette.Muted, style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.alerts_title), modifier = Modifier.semantics { heading() }, fontWeight = FontWeight.Bold)
            Text(stringResource(if (notifications) R.string.notifications_enabled else R.string.notifications_disabled),
                color = LinuxPalette.Secondary, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onNotifications, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("notification_settings")) {
                Text(stringResource(R.string.notification_settings), textAlign = TextAlign.Center)
            }
            Text(stringResource(if (exactAlarms) R.string.exact_alarms_enabled else R.string.exact_alarms_disabled),
                color = LinuxPalette.Secondary, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onExactAlarms, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("exact_alarm_settings")) {
                Text(stringResource(R.string.exact_alarm_settings), textAlign = TextAlign.Center)
            }
            Text(stringResource(R.string.background_limitations), color = LinuxPalette.Muted, style = MaterialTheme.typography.bodySmall)
            if (onLanguage != null) {
                OutlinedButton(onClick = onLanguage, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("language_settings")) {
                    Text(stringResource(R.string.language_settings))
                }
            }
            TextButton(onClick = onResetCycle, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("reset_cycle")) {
                Text(stringResource(R.string.reset_cycle))
            }
        }
    }
}

@Composable
private fun CycleSetting(label: String, description: String, tag: String, checked: Boolean, enabled: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag)
        .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChecked)
        .padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, color = if (enabled) LinuxPalette.Text else LinuxPalette.Muted)
            Text(description, color = LinuxPalette.Muted, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun DurationField(label: String, tag: String, value: String, enabled: Boolean, onValue: (String) -> Unit) {
    val valid = validMinutes(value.toIntOrNull())
    OutlinedTextField(value = value, enabled = enabled,
        onValueChange = { candidate -> if (candidate.length <= 3 && candidate.all { it in '0'..'9' }) onValue(candidate) },
        label = { Text(label) }, supportingText = { Text(stringResource(if (valid) R.string.duration_range else R.string.duration_error)) },
        isError = !valid, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().testTag(tag))
}

@Composable
private fun modeLabel(mode: TimerMode): String = stringResource(when (mode) {
    TimerMode.FOCUS -> R.string.mode_focus
    TimerMode.SHORT_BREAK -> R.string.mode_short_break
    TimerMode.LONG_BREAK -> R.string.mode_long_break
})
