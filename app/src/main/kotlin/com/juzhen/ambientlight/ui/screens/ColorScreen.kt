package com.juzhen.ambientlight.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.juzhen.ambient.protocol.PowerMode
import com.juzhen.ambient.protocol.Rgb
import com.juzhen.ambient.protocol.Zone
import com.juzhen.ambientlight.ui.LampUiState
import com.juzhen.ambientlight.ui.LampViewModel
import com.juzhen.ambientlight.ui.components.BigSlider
import com.juzhen.ambientlight.ui.components.ChipSelector
import com.juzhen.ambientlight.ui.components.HueWheel
import com.juzhen.ambientlight.ui.components.SectionTitle
import com.juzhen.ambientlight.ui.toComposeColor

private val Presets = listOf(
    Rgb(255, 0, 0),
    Rgb(255, 96, 0),
    Rgb(255, 200, 0),
    Rgb(0, 220, 90),
    Rgb(0, 200, 255),
    Rgb(40, 90, 255),
    Rgb(160, 60, 255),
    Rgb(255, 255, 255),
)

private val ZoneLabels = listOf("分区1", "分区2", "全区")

/** UI order deliberately matches the original app: 开启 / 关闭 / 跟随 -> 2 / 0 / 1. */
private val PowerOrder = listOf(PowerMode.ON, PowerMode.OFF, PowerMode.FOLLOW)
private val PowerLabels = listOf("开启", "关闭", "跟随")

@Composable
fun ColorScreen(ui: LampUiState, vm: LampViewModel, wide: Boolean) {
    if (wide) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            BoxWithConstraints(
                Modifier
                    .weight(1.15f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                val side = minOf(maxWidth, maxHeight)
                HueWheel(
                    hue = ui.hue,
                    modifier = Modifier.size(side),
                    onHueChange = vm::setHue,
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
            ) {
                ColorControls(ui, vm, Modifier.fillMaxWidth())
            }
        }
    } else {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                // cap the wheel so it still fits a landscape phone without scrolling forever
                val side = minOf(maxWidth, 320.dp)
                HueWheel(
                    hue = ui.hue,
                    modifier = Modifier.size(side),
                    onHueChange = vm::setHue,
                )
            }
            Spacer(Modifier.height(14.dp))
            ColorControls(ui, vm, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ColorControls(ui: LampUiState, vm: LampViewModel, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("分区")
        ChipSelector(
            options = ZoneLabels,
            selectedIndex = ui.zone.ordinal,
            onSelect = { index -> vm.setZone(Zone.entries[index]) },
        )

        SectionTitle("预设色")
        PresetRow(onPick = vm::setPreset)

        BigSlider(
            label = "亮度",
            value = ui.brightness.toFloat(),
            valueRange = 0f..100f,
            valueText = "${ui.brightness}",
            onValueChange = { vm.setBrightness(it.toInt()) },
        )

        SectionTitle("开关")
        ChipSelector(
            options = PowerLabels,
            selectedIndex = PowerOrder.indexOf(ui.power).coerceAtLeast(0),
            onSelect = { index -> vm.setPower(PowerOrder[index]) },
        )
    }
}

@Composable
private fun PresetRow(onPick: (Rgb) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Presets.forEach { rgb ->
            Box(
                Modifier
                    .size(46.dp)
                    .background(rgb.toComposeColor(), CircleShape)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                        shape = CircleShape,
                    )
                    .clickable { onPick(rgb) },
            )
        }
    }
}
