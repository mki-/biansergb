package com.juzhen.ambientlight.ui

import androidx.compose.ui.graphics.Color
import com.juzhen.ambient.protocol.Rgb
import kotlin.math.abs

/**
 * Small colour helpers. Kept dependency-free so they are trivially unit-testable if
 * we ever add JVM tests for the UI layer.
 */

fun hsvToRgb(hue: Float, saturation: Float, value: Float = 1f): Rgb {
    val h = ((hue % 360f) + 360f) % 360f
    val s = saturation.coerceIn(0f, 1f)
    val v = value.coerceIn(0f, 1f)
    val c = v * s
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = v - c
    val (r, g, b) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    fun q(f: Float): Int = ((f + m) * 255f).toInt().coerceIn(0, 255)
    return Rgb(q(r), q(g), q(b))
}

fun rgbToHue(rgb: Rgb): Float {
    val r = rgb.red / 255f
    val g = rgb.green / 255f
    val b = rgb.blue / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val d = max - min
    if (d < 1e-4f) return 0f
    val h = when (max) {
        r -> 60f * (((g - b) / d) % 6f)
        g -> 60f * (((b - r) / d) + 2f)
        else -> 60f * (((r - g) / d) + 4f)
    }
    return ((h % 360f) + 360f) % 360f
}

fun Rgb.toComposeColor(): Color = Color(red, green, blue)
