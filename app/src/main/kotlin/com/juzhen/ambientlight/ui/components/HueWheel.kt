package com.juzhen.ambientlight.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import com.juzhen.ambientlight.ui.hsvToRgb
import com.juzhen.ambientlight.ui.theme.LampSurface
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private val SweepColors = listOf(
    Color.Red,
    Color(0xFFFF8000),
    Color.Yellow,
    Color.Green,
    Color.Cyan,
    Color.Blue,
    Color.Magenta,
    Color.Red,
)

/**
 * Hue ring with a live preview disc in the middle.
 *
 * A tap or drag anywhere picks the hue by polar angle. Drawn with a plain Canvas so
 * it costs nothing on the low-power GPUs found in car head units.
 */
@Composable
fun HueWheel(
    hue: Float,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onHueChange: (Float) -> Unit,
) {
    Canvas(
        modifier = modifier
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures { change, _ ->
                    change.consume()
                    onHueChange(angleAt(change.position, size))
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset -> onHueChange(angleAt(offset, size)) }
            },
    ) {
        val radius = size.minDimension / 2f
        drawCircle(brush = Brush.sweepGradient(SweepColors), radius = radius)

        // inner disc = currently selected colour
        val inner = radius * 0.54f
        drawCircle(color = LampSurface, radius = inner)
        val rgb = hsvToRgb(hue, 1f)
        drawCircle(color = Color(rgb.red, rgb.green, rgb.blue), radius = inner * 0.84f)

        // thumb
        val rad = Math.toRadians(hue.toDouble())
        val point = Offset(
            x = center.x + (cos(rad).toFloat() * radius * 0.76f),
            y = center.y + (sin(rad).toFloat() * radius * 0.76f),
        )
        drawCircle(
            color = Color.Black.copy(alpha = 0.55f),
            radius = radius * 0.075f + 4f,
            center = point,
        )
        drawCircle(
            color = Color.White,
            radius = radius * 0.075f,
            center = point,
            style = Stroke(width = 3f),
        )
    }
}

private fun angleAt(offset: Offset, size: IntSize): Float {
    val dx = offset.x - size.width / 2f
    val dy = offset.y - size.height / 2f
    if (hypot(dx, dy) < 4f) return 0f
    val deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
    return (deg + 360f) % 360f
}
