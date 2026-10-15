// Copyright (c) 2026 Brewdoro contributors. SPDX-License-Identifier: MIT
package io.github.coreharbor.brewdoro.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Literal tokens from the Linux app's resources/style.css; never use dynamic colors. */
internal object LinuxPalette {
    val Background = Color(0xFF24283B)
    val Header = Color(0xFF1F2335)
    val Surface = Color(0xFF292E42)
    val SurfaceHover = Color(0xFF343A55)
    val SurfaceActive = Color(0xFF394B70)
    val Text = Color(0xFFC0CAF5)
    val Secondary = Color(0xFFA9B1D6)
    val Muted = Color(0xFF737AA2)
    val Accent = Color(0xFF3D59A1)
    val AccentHover = Color(0xFF4D6CB3)
    val AccentLight = Color(0xFF7AA2F7)
    val AccentText = Color(0xFFF4F6FF)
    val Coffee = Color(red = 0.878f, green = 0.686f, blue = 0.408f, alpha = 1f)
}

private val LinuxColors = darkColorScheme(
    primary = LinuxPalette.AccentLight,
    onPrimary = LinuxPalette.Header,
    primaryContainer = LinuxPalette.Accent,
    onPrimaryContainer = LinuxPalette.Text,
    secondary = LinuxPalette.Secondary,
    onSecondary = LinuxPalette.Background,
    secondaryContainer = LinuxPalette.SurfaceActive,
    onSecondaryContainer = LinuxPalette.Text,
    tertiary = LinuxPalette.AccentLight,
    onTertiary = LinuxPalette.Header,
    background = LinuxPalette.Background,
    onBackground = LinuxPalette.Text,
    surface = LinuxPalette.Surface,
    onSurface = LinuxPalette.Text,
    surfaceVariant = LinuxPalette.SurfaceHover,
    onSurfaceVariant = LinuxPalette.Secondary,
    surfaceTint = LinuxPalette.Surface,
    surfaceContainer = LinuxPalette.Surface,
    surfaceContainerLow = LinuxPalette.Surface,
    surfaceContainerHigh = LinuxPalette.Surface,
    surfaceContainerHighest = LinuxPalette.Surface,
    outline = LinuxPalette.Muted,
    outlineVariant = LinuxPalette.Muted,
)

@Composable
fun BrewdoroTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LinuxColors, content = content)
}
