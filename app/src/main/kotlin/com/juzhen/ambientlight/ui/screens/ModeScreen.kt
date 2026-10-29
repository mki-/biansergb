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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.juzhen.ambientlight.ui.LampUiState
import com.juzhen.ambientlight.ui.LampViewModel
import com.juzhen.ambientlight.ui.components.BigSlider
import com.juzhen.ambientlight.ui.components.Chip
import com.juzhen.ambientlight.ui.components.ChipSelector
import com.juzhen.ambientlight.ui.components.GlassCard
import com.juzhen.ambientlight.ui.components.SectionTitle
import com.juzhen.ambientlight.ui.components.ToggleRow
import com.juzhen.ambientlight.ui.theme.LampGradients

private val FlowFamilies = listOf("七彩", "幻彩", "魔幻", "律动", "跟随")
private val FlowSteps = listOf(1, 2, 3, 4, 5, 6)

@Composable
fun ModeScreen(ui: LampUiState, vm: LampViewModel, wide: Boolean) {
    if (wide) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
            ) {
                SlidersCard(ui, vm)
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SwitchesCard(ui, vm)
                FlowCard(ui, vm)
                ExtrasCard(vm)
            }
        }
    } else {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SlidersCard(ui, vm)
            SwitchesCard(ui, vm)
            FlowCard(ui, vm)
            ExtrasCard(vm)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SlidersCard(ui: LampUiState, vm: LampViewModel) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
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
    }
}

@Composable
private fun SwitchesCard(ui: LampUiState, vm: LampViewModel) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
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
    }
}

@Composable
private fun FlowCard(ui: LampUiState, vm: LampViewModel) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "流水模式",
                style = MaterialTheme.typography.titleMedium.copy(brush = LampGradients.multi),
            )
            ChipSelector(
                options = FlowFamilies,
                selectedIndex = ui.flowFamily,
                onSelect = vm::selectFlowFamily,
            )
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
                        gradient = LampGradients.cyanBlue,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExtrasCard(vm: LampViewModel) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SectionTitle("高级")
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Chip(
                    label = "内置模式",
                    selected = false,
                    onClick = vm::sendBuiltinMode,
                    modifier = Modifier.weight(1f),
                    gradient = LampGradients.cyanBlue,
                )
                Chip(
                    label = "RF 配对",
                    selected = false,
                    onClick = vm::sendRfPair,
                    modifier = Modifier.weight(1f),
                    gradient = LampGradients.pinkOrange,
                )
            }
        }
    }
}
