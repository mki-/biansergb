package com.juzhen.ambientlight.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Modern Gradient design system, translated from the Tailwind spec into Compose
 * tokens. The Tailwind class names are kept in the comments so the mapping stays
 * auditable.
 *
 * Rules honoured here:
 *  - main surfaces are never flat (`bg-slate-950` only shows through the background
 *    gradient + glow orbs),
 *  - no grey-only palette: every neutral is white-with-alpha over colour,
 *  - corners are `rounded-2xl` (16dp) / `rounded-3xl` (24dp), never square.
 */

// --- palette ---------------------------------------------------------------
val Slate950 = Color(0xFF020617)          // bg-slate-950
val Slate900 = Color(0xFF0F172A)          // bg-slate-900
val InkIndigo = Color(0xFF1E1B4B)         // bg-[#1e1b4b]

val Violet500 = Color(0xFF8B5CF6)         // violet-500
val Violet600 = Color(0xFF7C3AED)         // violet-600
val Purple500 = Color(0xFFA855F7)         // purple-500
val Fuchsia500 = Color(0xFFD946EF)        // fuchsia-500
val Fuchsia600 = Color(0xFFC026D3)        // fuchsia-600
val Cyan500 = Color(0xFF06B6D4)           // cyan-500
val Blue500 = Color(0xFF3B82F6)           // blue-500
val Pink500 = Color(0xFFEC4899)           // pink-500
val Orange500 = Color(0xFFF97316)         // orange-500

/** Status colours brightened for a dark surface (WCAG AA on #020617). */
val OkGreen = Color(0xFF34D399)
val WarnAmber = Color(0xFFFBBF24)
val ErrRed = Color(0xFFF87171)

// --- legacy names kept so the rest of the app keeps compiling --------------
// They now map onto glass layers instead of solid greys.
val LampBg: Color = Slate950
val LampSurface: Color = Color.White.copy(alpha = 0.08f)      // glass base
val LampSurfaceHigh: Color = Color.White.copy(alpha = 0.14f)  // raised glass
val LampPrimary: Color = Violet500
val LampAccent: Color = Fuchsia500
val LampOn: Color = Color.White
val LampMuted: Color = Color.White.copy(alpha = 0.70f)        // text-white/70

/** Glass card tokens: `backdrop-blur-xl bg-white/10 border border-white/20 rounded-3xl`. */
val GlassBorder = Color.White.copy(alpha = 0.20f)
val GlassBorderStrong = Color.White.copy(alpha = 0.30f)

object LampGradients {

    /** from-violet-600 to-fuchsia-600 — primary button / active tab. */
    val violetFuchsia: Brush = Brush.horizontalGradient(listOf(Violet600, Fuchsia600))

    /** from-violet-500 via-purple-500 to-fuchsia-500 — brand accents. */
    val brand: Brush = Brush.horizontalGradient(listOf(Violet500, Purple500, Fuchsia500))

    /** from-violet-500 via-fuchsia-500 to-cyan-500 — multi-colour headline. */
    val multi: Brush = Brush.horizontalGradient(listOf(Violet500, Fuchsia500, Cyan500))

    /** from-cyan-500 to-blue-500 — secondary actions. */
    val cyanBlue: Brush = Brush.horizontalGradient(listOf(Cyan500, Blue500))

    /** from-pink-500 to-orange-500 — warm accent. */
    val pinkOrange: Brush = Brush.horizontalGradient(listOf(Pink500, Orange500))

    /** glass sheen: white/16 -> white/06 reads more like `backdrop-blur` than a flat tint. */
    val glassFill: Brush = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.06f)),
    )

    /** page background: #1e1b4b -> slate-950 (no flat fill on the main area). */
    val background: Brush = Brush.verticalGradient(
        0f to InkIndigo,
        0.55f to Color(0xFF120F35),
        1f to Slate950,
    )
}

private val LampColorScheme = darkColorScheme(
    primary = Violet500,
    onPrimary = Color.White,
    primaryContainer = Violet600,
    onPrimaryContainer = Color.White,
    secondary = Fuchsia500,
    onSecondary = Color.White,
    tertiary = Cyan500,
    onTertiary = Color.White,
    background = Slate950,
    onBackground = Color.White,
    surface = Color(0xFF1A1633),
    onSurface = Color.White,
    surfaceVariant = Color.White.copy(alpha = 0.14f),
    onSurfaceVariant = Color.White.copy(alpha = 0.70f),
    outline = Color.White.copy(alpha = 0.28f),
    error = ErrRed,
    onError = Slate950,
)

/**
 * One step larger than Material defaults: the primary target is a dashboard screen
 * read at arm's length. Weights follow the spec (headings semibold, body normal,
 * actions medium).
 */
private val LampTypography = Typography(
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Normal),
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
