package com.juzhen.ambientlight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.juzhen.ambientlight.ble.LinkState
import com.juzhen.ambientlight.ui.screens.AboutScreen
import com.juzhen.ambientlight.ui.screens.ColorScreen
import com.juzhen.ambientlight.ui.screens.DeviceScreen
import com.juzhen.ambientlight.ui.screens.ModeScreen
import com.juzhen.ambientlight.ui.theme.ErrRed
import com.juzhen.ambientlight.ui.theme.LampBg
import com.juzhen.ambientlight.ui.theme.LampMuted
import com.juzhen.ambientlight.ui.theme.LampPrimary
import com.juzhen.ambientlight.ui.theme.LampSurface
import com.juzhen.ambientlight.ui.theme.OkGreen
import com.juzhen.ambientlight.ui.theme.WarnAmber

enum class LampTab(val label: String) {
    COLOR("色彩"),
    MODE("模式"),
    DEVICE("设备"),
    ABOUT("关于"),
}

/** Above this width the preset grid gets 8 columns instead of 4. */
private val WideWidth: Dp = 640.dp

/**
 * One shell for every device: a tab bar pinned to the top, a thin status line, then
 * content. There is no side rail and no bottom bar — on an 800x480 dashboard the
 * vertical space is the scarce resource, and a top menu is what the car UI needs.
 */
@Composable
fun AmbientLampApp(vm: LampViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var tabName by rememberSaveable { mutableStateOf(LampTab.COLOR.name) }
    val tab = LampTab.entries.firstOrNull { it.name == tabName } ?: LampTab.COLOR
    val snackbar = remember { SnackbarHostState() }
    val view = LocalView.current

    // A lamp controller is usually mounted and watched; keep the panel awake while
    // the light is actually under control.
    LaunchedEffect(ui.isReady) { view.keepScreenOn = ui.isReady }

    LaunchedEffect(ui.message) {
        val message = ui.message
        if (message != null) {
            snackbar.showSnackbar(message)
            vm.clearMessage()
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val wide = maxWidth >= WideWidth
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                TopTabs(tab) { tabName = it.name }
                StatusRow(ui, vm)
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    when (tab) {
                        LampTab.COLOR -> ColorScreen(ui, vm, wide = wide)
                        LampTab.MODE -> ModeScreen(ui, vm, wide = wide)
                        LampTab.DEVICE -> DeviceScreen(ui, vm, wide = wide)
                        LampTab.ABOUT -> AboutScreen()
                    }
                }
            }
        }
    }
}

@Composable
private fun TopTabs(selected: LampTab, onSelect: (LampTab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(LampSurface)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LampTab.entries.forEach { tab ->
            val active = tab == selected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                        RoundedCornerShape(12.dp),
                    )
                    .clickable { onSelect(tab) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = tab.label,
                    color = if (active) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun StatusRow(ui: LampUiState, vm: LampViewModel) {
    val (text, color) = statusOf(ui.link)
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(LampBg)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .background(color, CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = if (ui.isReady) vm::disconnect else vm::autoConnectLast,
            enabled = ui.isReady || ui.savedAddress != null,
        ) {
            Text(
                text = if (ui.isReady) "断开" else "重连",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

private fun statusOf(link: LinkState): Pair<String, Color> = when (link) {
    is LinkState.Ready -> "已连接  ${link.address}" to OkGreen
    is LinkState.Connecting -> "连接中 第 ${link.attempt} 次…" to WarnAmber
    is LinkState.Handshaking -> "握手中…" to WarnAmber
    is LinkState.Backoff -> "等待重连 ${link.retryInMs / 1000}s" to WarnAmber
    is LinkState.Scanning -> "正在搜索…" to LampPrimary
    is LinkState.Failed -> "失败：${link.reason}" to ErrRed
    LinkState.Idle -> "未连接" to LampMuted
}
