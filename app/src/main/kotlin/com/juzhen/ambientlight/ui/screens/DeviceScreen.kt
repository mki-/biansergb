package com.juzhen.ambientlight.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.juzhen.ambientlight.ble.LinkState
import com.juzhen.ambientlight.ui.LampUiState
import com.juzhen.ambientlight.ui.LampViewModel
import com.juzhen.ambientlight.ui.components.ColorDot
import com.juzhen.ambientlight.ui.components.Divider
import com.juzhen.ambientlight.ui.components.SectionTitle
import com.juzhen.ambientlight.ui.components.StatRow
import com.juzhen.ambientlight.ui.theme.LampSurface
import com.juzhen.ambientlight.ui.theme.LampSurfaceHigh
import com.juzhen.ambientlight.ui.toComposeColor

@Composable
fun DeviceScreen(ui: LampUiState, vm: LampViewModel, wide: Boolean) {
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
            ) {
                Actions(ui, vm)
                Spacer(Modifier.height(12.dp))
                DeviceInfo(ui, vm)
            }
            Column(
                Modifier
                    .weight(1.15f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
            ) {
                SectionTitle("扫描结果（${ui.hits.size}）")
                Spacer(Modifier.height(8.dp))
                ScanRows(ui, vm)
            }
        }
    } else {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            Actions(ui, vm)
            Spacer(Modifier.height(12.dp))
            SectionTitle("扫描结果（${ui.hits.size}）")
            Spacer(Modifier.height(8.dp))
            ScanRows(ui, vm)
            Divider(Modifier.padding(vertical = 12.dp))
            DeviceInfo(ui, vm)
        }
    }
}

@Composable
private fun Actions(ui: LampUiState, vm: LampViewModel) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Button(
            onClick = vm::startScan,
            modifier = Modifier.weight(1f),
        ) { Text("搜索设备") }
        OutlinedButton(
            onClick = vm::stopScan,
            modifier = Modifier.weight(1f),
        ) { Text("停止") }
        OutlinedButton(
            onClick = { if (ui.isReady) vm.disconnect() else vm.autoConnectLast() },
            enabled = ui.isReady || ui.savedAddress != null,
            modifier = Modifier.weight(1f),
        ) { Text(if (ui.isReady) "断开" else "重连") }
    }
}

/**
 * Plain rows (not a LazyColumn): the scan list is capped at a couple of dozen
 * entries, and a LazyColumn nested inside a scrolling column would fight over
 * infinite height constraints on short car screens.
 */
@Composable
private fun ScanRows(ui: LampUiState, vm: LampViewModel) {
    if (ui.hits.isEmpty()) {
        Text(
            text = "点“搜索设备”开始扫描",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val connectedAddress = (ui.link as? LinkState.Ready)?.address
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ui.hits.forEach { hit ->
            val isConnected = connectedAddress == hit.address
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .background(
                        if (isConnected) LampSurfaceHigh else LampSurface,
                        RoundedCornerShape(10.dp),
                    )
                    .clickable { vm.connect(hit.address) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = hit.name ?: "(未命名设备)",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = hit.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "${hit.rssi} dBm",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun DeviceInfo(ui: LampUiState, vm: LampViewModel) {
    Column(Modifier.fillMaxWidth()) {
        SectionTitle("设备状态")
        Spacer(Modifier.height(8.dp))

        val state = ui.deviceState
        if (state == null) {
            Text(
                text = if (ui.isReady) "已连接，等待设备上报状态…" else "未连接",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            ZoneLine(
                dot = state.zone1.toComposeColor(),
                text = "分区1  亮度 ${state.brightness1}   RGB ${state.zone1.red},${state.zone1.green},${state.zone1.blue}",
            )
            Spacer(Modifier.height(4.dp))
            ZoneLine(
                dot = state.zone2.toComposeColor(),
                text = "分区2  亮度 ${state.brightness2}   RGB ${state.zone2.red},${state.zone2.green},${state.zone2.blue}",
            )
            Spacer(Modifier.height(8.dp))
            StatRow("开关", if (state.power) "开" else "关")
            StatRow("模式", "${state.mode1} / ${state.mode2}")
            StatRow("麦克风电平", "${state.micLevel}")
        }

        if (ui.reports.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            SectionTitle("设备信息")
            Spacer(Modifier.height(6.dp))
            ui.reports.forEach { report ->
                Text(
                    text = report,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (ui.isReady) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = vm::refreshState) { Text("刷新状态") }
        }
    }
}

@Composable
private fun ZoneLine(dot: androidx.compose.ui.graphics.Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ColorDot(dot)
        Text(
            text = "  $text",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
