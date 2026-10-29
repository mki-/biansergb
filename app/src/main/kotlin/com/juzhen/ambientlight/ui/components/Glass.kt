package com.juzhen.ambientlight.ui.components

import android.animation.ValueAnimator
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.juzhen.ambientlight.ui.theme.Cyan500
import com.juzhen.ambientlight.ui.theme.Fuchsia500
import com.juzhen.ambientlight.ui.theme.GlassBorder
import com.juzhen.ambientlight.ui.theme.GlassBorderStrong
import com.juzhen.ambientlight.ui.theme.LampGradients
import com.juzhen.ambientlight.ui.theme.Violet500

/** `rounded-2xl` */
val Shape2Xl: Shape = RoundedCornerShape(16.dp)

/** `rounded-3xl` */
val Shape3Xl: Shape = RoundedCornerShape(24.dp)

/** Respects the system "remove animations" accessibility setting. */
@Composable
fun reducedMotion(): Boolean = remember {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        !ValueAnimator.areAnimatorsEnabled()
    } else {
        false
    }
}

/**
 * Page background: the `#1e1b4b -> slate-950` gradient plus three blurred colour
 * orbs (`blur-3xl opacity-30` written as radial gradients, which composite far
 * cheaper than a real blur on the low-end GPUs found in head units).
 */
@Composable
fun GlowBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .matchParentSize()
                .background(LampGradients.background),
        )
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height

            fun orb(center: Offset, radius: Float, color: Color, alpha: Float) {
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to color.copy(alpha = alpha),
                        1f to color.copy(alpha = 0f),
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = center,
                )
            }

            orb(Offset(w * 0.10f, h * 0.06f), w * 0.46f, Violet500, 0.34f)
            orb(Offset(w * 0.92f, h * 0.18f), w * 0.40f, Fuchsia500, 0.30f)
            orb(Offset(w * 0.60f, h * 1.02f), w * 0.52f, Cyan500, 0.24f)
        }
        content()
    }
}

/** `backdrop-blur-xl bg-white/10 border border-white/20 rounded-3xl` */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = Shape3Xl,
    borderColor: Color = GlassBorder,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .clip(shape)
            .background(LampGradients.glassFill)
            .border(1.dp, borderColor, shape),
        content = content,
    )
}

/**
 * Primary action: gradient fill, `rounded-2xl`, coloured shadow that blooms on press
 * (`Neon Dispersion`) and a moving highlight band (`Gradient Flow`) instead of a
 * plain brightness change.
 */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    brush: Brush = LampGradients.violetFuchsia,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduced = reducedMotion()

    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = if (reduced) snap() else tween(durationMillis = 200),
    )
    val flow by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = if (reduced) snap() else tween(durationMillis = 500),
    )

    val shape = Shape2Xl
    Box(
        modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
                alpha = if (enabled) 1f else 0.45f
            }
            .then(
                if (enabled) {
                    Modifier.shadow(
                        elevation = 14.dp,
                        shape = shape,
                        ambientColor = Violet500,
                        spotColor = Fuchsia500,
                    )
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .drawBehind {
                drawRect(brush = brush)
                if (flow > 0f) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0f to Color.White.copy(alpha = 0f),
                            0.5f to Color.White.copy(alpha = 0.25f * flow),
                            1f to Color.White.copy(alpha = 0f),
                            startX = -size.width + size.width * 2f * flow,
                            endX = size.width * 2f * flow,
                        ),
                    )
                }
            }
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}

/**
 * Selectable chip: glass when idle, gradient pill when selected (`Zero-Gravity Float`
 * on press + `Luminous Borders` when active).
 */
@Composable
fun PillChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    gradient: Brush = LampGradients.violetFuchsia,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduced = reducedMotion()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = if (reduced) snap() else tween(durationMillis = 180),
    )

    val shape = Shape2Xl
    Box(
        modifier
            .heightIn(min = 48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.45f
            }
            .then(
                if (selected) {
                    Modifier.shadow(
                        elevation = 12.dp,
                        shape = shape,
                        ambientColor = Violet500,
                        spotColor = Fuchsia500,
                    )
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .then(
                if (selected) {
                    Modifier.background(gradient, shape)
                } else {
                    Modifier.background(LampGradients.glassFill, shape)
                },
            )
            .border(
                width = 1.dp,
                color = if (selected) GlassBorderStrong else GlassBorder,
                shape = shape,
            )
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.80f),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}
