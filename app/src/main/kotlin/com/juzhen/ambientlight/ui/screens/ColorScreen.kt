package com.juzhen.ambientlight.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.juzhen.ambient.protocol.PowerMode
import com.juzhen.ambient.protocol.Zone
import com.juzhen.ambientlight.ui.LampUiState
import com.juzhen.ambientlight.ui.LampViewModel
import com.juzhen.ambientlight.ui.Preset
import com.juzhen.ambientlight.ui.PresetColors
import com.juzhen.ambientlight.ui.components.Chip
import com.juzhen.ambientlight.ui.theme.LampSurface
import com.juzhen.ambientlight.ui.toComposeColor

private val ZoneLabels = listOf("分区1", "分区2", "全区")

/** UI order deliberately matches the original app: 开启 / 关闭 / 跟随 -> 2 / 0 / 1. */
private val PowerOrder = listOf(PowerMode.ON, PowerMode.OFF, PowerMode.FOLLOW)
private val PowerLabels = listOf("开启", "关闭", "跟随")

/**
 * Colour page: 32 mainstream presets filling the centre, one compact control bar at
 * the bottom.
 *
 * The hue wheel is gone on purpose — a rotating gradient ring is a precision gesture
 * that is awkward on a dashboard, while 32 taps are faster and never miss. The column
 * count follows the width (8 for a 800x480 car screen, 4 for a phone), so the same
 * palette works on both without a second layout.
 */
@Composable
fun ColorScreen(ui: LampUiState, vm: LampViewModel, wide: Boolean) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = if (wide) 8 else 4
        val gap: Dp = if (wide) 10.dp else 8.dp
        val rows = (PresetColors.size + columns - 1) / columns

        // Control bar needs room for its rows; stack the chip rows on narrow screens.
        val stacked = maxWidth < 480.dp
        val controlBarHeight: Dp = if (stacked) 168.dp else 116.dp

        val pad = 10.dp
        val gridHeight = maxHeight - controlBarHeight
        val cellWidth = (maxWidth - pad * 2 - gap * (columns - 1)) / columns
        val cellHeight = (gridHeight - pad - gap * (rows - 1)) / rows
        val diameter = minOf(cellWidth, cellHeight).coerceIn(30.dp, 118.dp)

        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = pad, vertical = pad / 2),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PresetColors.chunked(columns).forEachIndexed { rowIndex, rowPresets ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(gap),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        rowPresets.forEachIndexed { colIndex, preset ->
                            val index = rowIndex * columns + colIndex
                            Swatch(
                                diameter = diameter,
                                preset = preset,
                                selected = ui.presetIndex == index,
                                onClick = { vm.selectPreset(index) },
                            )
                        }
                    }
                    if (rowIndex != rows - 1) Spacer(Modifier.height(gap))
                }
            }
            ControlBar(
                ui = ui,
                vm = vm,
                stacked = stacked,
                modifier = Modifier.height(controlBarHeight),
            )
        }
    }
}

@Composable
private fun Swatch(
    diameter: Dp,
    preset: Preset,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val fill = preset.rgb.toComposeColor()
    // marker colour that stays visible on both very light and very dark swatches
    val luminance =
        (0.299f * preset.rgb.red + 0.587f * preset.rgb.green + 0.114f * preset.rgb.blue) / 255f
    val marker = if (luminance > 0.6f) Color.Black else Color.White

    Box(
        Modifier
            .size(diameter)
            .background(fill, CircleShape)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) marker else Color.White.copy(alpha = 0.22f),
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                Modifier
                    .size(diameter * 0.26f)
                    .background(marker, CircleShape),
            )
        }
    }
}

@Composable
private fun ControlBar(
    ui: LampUiState,
    vm: LampViewModel,
    stacked: Boolean,
    modifier: Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(LampSurface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (stacked) {
            ZoneRow(ui, vm)
            PowerRow(ui, vm)
        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ZoneLabels.forEachIndexed { index, label ->
                    Chip(
                        label = label,
                        selected = ui.zone.ordinal == index,
                        onClick = { vm.setZone(Zone.entries[index]) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.width(12.dp))
                PowerLabels.forEachIndexed { index, label ->
                    Chip(
                        label = label,
                        selected = ui.power == PowerOrder[index],
                        onClick = { vm.setPower(PowerOrder[index]) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        BrightnessRow(ui, vm)
    }
}

@Composable
private fun ZoneRow(ui: LampUiState, vm: LampViewModel) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZoneLabels.forEachIndexed { index, label ->
            Chip(
                label = label,
                selected = ui.zone.ordinal == index,
                onClick = { vm.setZone(Zone.entries[index]) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PowerRow(ui: LampUiState, vm: LampViewModel) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PowerLabels.forEachIndexed { index, label ->
            Chip(
                label = label,
                selected = ui.power == PowerOrder[index],
                onClick = { vm.setPower(PowerOrder[index]) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BrightnessRow(ui: LampUiState, vm: LampViewModel) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "亮度",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(52.dp),
        )
        Slider(
            value = ui.brightness.toFloat(),
            onValueChange = { vm.setBrightness(it.toInt()) },
            valueRange = 0f..100f,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${ui.brightness}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.End,
            modifier = Modifier.width(46.dp),
        )
    }
}
