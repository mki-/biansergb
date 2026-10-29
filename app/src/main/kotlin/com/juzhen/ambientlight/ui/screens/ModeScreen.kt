package com.juzhen.ambientlight.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.juzhen.ambientlight.ui.LampUiState
import com.juzhen.ambientlight.ui.LampViewModel
import com.juzhen.ambientlight.ui.components.BigSlider
import com.juzhen.ambientlight.ui.components.Chip
import com.juzhen.ambientlight.ui.components.ChipSelector
import com.juzhen.ambientlight.ui.components.Divider
import com.juzhen.ambientlight.ui.components.SectionTitle
import com.juzhen.ambientlight.ui.components.ToggleRow

private val FlowFamilies = listOf("七彩", "幻彩", "魔幻", "律动", "跟随")
private val FlowSteps = listOf(1, 2, 3, 4, 5, 6)

@Composable
fun ModeScreen(ui: LampUiState, vm: LampViewModel, wide: Boolean) {
    if (wide) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Sliders(ui, vm)
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Switches(ui, vm)
                Divider(Modifier.padding(vertical = 6.dp))
                FlowPicker(ui, vm)
                Divider(Modifier.padding(vertical = 6.dp))
                Extras(vm)
            }
        }
    } else {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Sliders(ui, vm)
            Divider(Modifier.padding(vertical = 6.dp))
            Switches(ui, vm)
            Divider(Modifier.padding(vertical = 6.dp))
            FlowPicker(ui, vm)
            Divider(Modifier.padding(vertical = 6.dp))
            Extras(vm)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Sliders(ui: LampUiState, vm: LampViewModel) {
    BigSlider(
        label = "速度",
        value = ui.speed.toFloat(),
        valueRange = 0f..5f,
        valueText = "${ui.speed}",
        steps = 4,
        onValueChange = { vm.setSpeed(it.toInt()) },
    )
    BigSlider(
        label = "灯珠数量",
        value = ui.ledCount.toFloat(),
        valueRange = 0f..150f,
        valueText = "${ui.ledCount}",
        onValueChange = { vm.setLedCount(it.toInt()) },
    )
    BigSlider(
        label = "声音灵敏度",
        value = ui.micSensitivity.toFloat(),
        valueRange = 0f..100f,
        valueText = "${ui.micSensitivity}",
        onValueChange = { vm.setMicSensitivity(it.toInt()) },
    )
}

@Composable
private fun Switches(ui: LampUiState, vm: LampViewModel) {
    ToggleRow(
        label = "反向",
        checked = ui.reverse,
        onCheckedChange = { vm.setDirection(it) },
    )
    ToggleRow(
        label = "转向跟随",
        checked = ui.rotationFollow,
        onCheckedChange = { vm.setRotationFollow(it) },
    )
}

@Composable
private fun FlowPicker(ui: LampUiState, vm: LampViewModel) {
    SectionTitle("流水模式")
    ChipSelector(
        options = FlowFamilies,
        selectedIndex = ui.flowFamily,
        onSelect = vm::selectFlowFamily,
    )
    Spacer(Modifier.height(8.dp))
    SectionTitle("档位（点击立即下发）")
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FlowSteps.forEach { step ->
            Chip(
                label = "$step",
                selected = false,
                onClick = { vm.sendFlowStep(step) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Extras(vm: LampViewModel) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedButton(
            onClick = vm::sendBuiltinMode,
            modifier = Modifier.weight(1f),
        ) { Text("内置模式") }
        OutlinedButton(
            onClick = vm::sendRfPair,
            modifier = Modifier.weight(1f),
        ) { Text("RF 配对") }
    }
}
