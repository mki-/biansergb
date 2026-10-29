package com.juzhen.ambientlight.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

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

/**
 * Everything is one notch bigger than the Material defaults: the primary target is a
 * dashboard screen viewed at arm's length, sometimes while the car is moving.
 */
private val LampTypography = Typography(
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 14.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun AmbientLampTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LampColorScheme,
        typography = LampTypography,
        content = content,
    )
}
