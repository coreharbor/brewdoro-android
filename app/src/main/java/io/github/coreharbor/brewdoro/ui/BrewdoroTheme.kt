// Copyright (c) 2026 Brewdoro contributors. SPDX-License-Identifier: MIT
package io.github.coreharbor.brewdoro.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CoffeeColors = darkColorScheme(
    primary = Color(0xFFE9BE91),
    onPrimary = Color(0xFF3D2617),
    primaryContainer = Color(0xFF58402D),
    onPrimaryContainer = Color(0xFFFFDCB9),
    secondary = Color(0xFFD4C4AF),
    onSecondary = Color(0xFF382F23),
    secondaryContainer = Color(0xFF504637),
    onSecondaryContainer = Color(0xFFF2E0C9),
    tertiary = Color(0xFFBFCB9D),
    onTertiary = Color(0xFF293316),
    background = Color(0xFF171310),
    onBackground = Color(0xFFEDE0D6),
    surface = Color(0xFF211B17),
    onSurface = Color(0xFFEDE0D6),
    surfaceVariant = Color(0xFF4C4138),
    onSurfaceVariant = Color(0xFFD1C3B7),
    outline = Color(0xFF9D8E80),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun BrewdoroTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CoffeeColors, content = content)
}
