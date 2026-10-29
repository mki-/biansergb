package com.juzhen.ambientlight.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.juzhen.ambientlight.ui.theme.Fuchsia500
import com.juzhen.ambientlight.ui.theme.LampGradients
import com.juzhen.ambientlight.ui.theme.Slate900
import com.juzhen.ambientlight.ui.theme.Violet500

/** Section label — white/60, never uppercase-tracked eyebrow text. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = Color.White.copy(alpha = 0.60f),
        modifier = modifier,
    )
}

/** Hairline separator: white/10 instead of a grey line. */
@Composable
fun Divider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.10f)),
    )
}

/**
 * Label + value on one line, slider underneath.
 *
 * Sized for a moving vehicle: the row is at least 56dp tall and the slider gets the
 * full width instead of being squeezed next to its label. Track/thumb use the brand
 * gradient's solid anchors (a slider thumb cannot take a brush).
 */
@Composable
fun BigSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueText: String,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    enabled: Boolean = true,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.80f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelLarge,
                color = Violet500,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = Fuchsia500,
                activeTrackColor = Violet500,
                inactiveTrackColor = Color.White.copy(alpha = 0.16f),
                activeTickColor = Color.White.copy(alpha = 0.40f),
                inactiveTickColor = Color.White.copy(alpha = 0.20f),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp),
        )
    }
}

@Composable
fun ToggleRow(
    label: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.80f),
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Violet500,
                checkedBorderColor = Color.White.copy(alpha = 0.30f),
                uncheckedThumbColor = Color.White.copy(alpha = 0.80f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.10f),
                uncheckedBorderColor = Color.White.copy(alpha = 0.24f),
            ),
        )
    }
}

/**
 * Single-select chip row. Scrolls horizontally so a narrow screen never breaks the
 * layout, while an 800dp car screen simply shows everything at once.
 */
@Composable
fun ChipSelector(
    options: List<String>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEachIndexed { index, label ->
            Chip(
                label = label,
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
            )
        }
    }
}

/**
 * Selectable pill. Glass when idle, brand gradient when selected — the single
 * component every screen uses for zone / power / mode choices.
 */
@Composable
fun Chip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    gradient: Brush = LampGradients.violetFuchsia,
) {
    PillChip(
        label = label,
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        gradient = gradient,
    )
}

@Composable
fun ColorDot(color: Color, size: Int = 18, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size.dp)
            .background(color, CircleShape),
    )
}

@Composable
fun StatRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.60f),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Unused-but-exported helper kept for the About screen's gradient accents. */
@Composable
fun BrandAccentDot(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(10.dp)
            .background(Slate900, CircleShape),
    )
}
