package com.juzhen.ambientlight.ui

import androidx.compose.ui.graphics.Color
import com.juzhen.ambient.protocol.Rgb
import kotlin.math.abs

/** Bridge between the protocol's RGB model and Compose. */
fun Rgb.toComposeColor(): Color = Color(red, green, blue)

/**
 * Fully saturated, fully bright colour for a hue in degrees.
 *
 * Used by the desktop cards' colour strip: a segment carries a hue angle, not a preset
 * index, because the strip is not limited to [PresetColors].
 */
fun hueToRgb(hueDegrees: Float): Rgb {
    val h = ((hueDegrees % 360f) + 360f) % 360f
    val x = 1f - abs((h / 60f) % 2f - 1f)
    val (r, g, b) = when {
        h < 60f -> Triple(1f, x, 0f)
        h < 120f -> Triple(x, 1f, 0f)
        h < 180f -> Triple(0f, 1f, x)
        h < 240f -> Triple(0f, x, 1f)
        h < 300f -> Triple(x, 0f, 1f)
        else -> Triple(1f, 0f, x)
    }
    fun q(f: Float): Int = (f * 255f).toInt().coerceIn(0, 255)
    return Rgb(q(r), q(g), q(b))
}
