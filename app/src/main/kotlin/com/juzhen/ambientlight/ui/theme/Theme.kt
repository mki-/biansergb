package com.juzhen.ambientlight.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The app is always dark: it is a lighting controller, and both car head units and
 * phones should not blast a white screen. Colours are chosen to stay legible on the
 * cheap TN panels common in 800x480 head units (high contrast, no subtle greys).
 */
val LampBg = Color(0xFF0E0F14)
val LampSurface = Color(0xFF171A22)
val LampSurfaceHigh = Color(0xFF232735)
val LampPrimary = Color(0xFF4FC3F7)
val LampAccent = Color(0xFFFFC107)
val LampOn = Color(0xFFE6E8EF)
val LampMuted = Color(0xFF9AA3B2)

val OkGreen = Color(0xFF66BB6A)
val WarnAmber = Color(0xFFFFB74D)
val ErrRed = Color(0xFFEF5350)

private val LampColorScheme = darkColorScheme(
    primary = LampPrimary,
    onPrimary = Color(0xFF00212E),
    secondary = LampAccent,
    onSecondary = Color(0xFF2A1D00),
    background = LampBg,
    onBackground = LampOn,
    surface = LampSurface,
    onSurface = LampOn,
    surfaceVariant = LampSurfaceHigh,
    onSurfaceVariant = LampMuted,
    error = ErrRed,
    onError = Color(0xFF2A0000),
)

@Composable
fun AmbientLampTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LampColorScheme,
        typography = Typography(),
        content = content,
    )
}
