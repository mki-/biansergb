package com.juzhen.ambientlight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.juzhen.ambientlight.ble.LinkState
import com.juzhen.ambientlight.ui.components.GlassCard
import com.juzhen.ambientlight.ui.components.GlowBackground
import com.juzhen.ambientlight.ui.components.Shape2Xl
import com.juzhen.ambientlight.ui.screens.AboutScreen
import com.juzhen.ambientlight.ui.screens.ColorScreen
import com.juzhen.ambientlight.ui.screens.DeviceScreen
import com.juzhen.ambientlight.ui.screens.ModeScreen
import com.juzhen.ambientlight.ui.theme.ErrRed
import com.juzhen.ambientlight.ui.theme.Fuchsia500
import com.juzhen.ambientlight.ui.theme.GlassBorder
import com.juzhen.ambientlight.ui.theme.LampGradients
import com.juzhen.ambientlight.ui.theme.OkGreen
import com.juzhen.ambientlight.ui.theme.Violet500
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
 * One shell everywhere: glass tab bar on top, a thin status line, then content on a
 * gradient + glow background. No side rail, no bottom bar — on an 800x480 dashboard
 * vertical space is the scarce resource.
 */
@Composable
fun AmbientLampApp(vm: LampViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var tabName by rememberSaveable { mutableStateOf(LampTab.COLOR.name) }
    val tab = LampTab.entries.firstOrNull { it.name == tabName } ?: LampTab.COLOR
    val snackbar = remember { SnackbarHostState() }
    val view = LocalView.current

    LaunchedEffect(ui.isReady) { view.keepScreenOn = ui.isReady }

    LaunchedEffect(ui.message) {
        val message = ui.message
        if (message != null) {
            snackbar.showSnackbar(message)
            vm.clearMessage()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= WideWidth
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            GlowBackground(Modifier.padding(padding)) {
                Column(Modifier.fillMaxSize()) {
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
}

/** Glass bar holding four gradient pills. */
@Composable
private fun TopTabs(selected: LampTab, onSelect: (LampTab) -> Unit) {
    GlassCard(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .height(54.dp),
        shape = Shape2Xl,
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LampTab.entries.forEach { tab ->
                TabPill(
                    label = tab.label,
                    active = tab == selected,
                    onClick = { onSelect(tab) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun TabPill(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .then(
                if (active) {
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
                if (active) {
                    Modifier.background(LampGradients.violetFuchsia, shape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (active) Color.White else Color.White.copy(alpha = 0.65f),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
        )
    }
}

@Composable
private fun StatusRow(ui: LampUiState, vm: LampViewModel) {
    val (text, color) = statusOf(ui.link)
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .background(color, CircleShape)
                .shadow(6.dp, CircleShape, ambientColor = color, spotColor = color),
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
        Box(
            Modifier
                .clip(Shape2Xl)
                .background(LampGradients.glassFill, Shape2Xl)
                .border(1.dp, GlassBorder, Shape2Xl)
                .clickable(enabled = ui.isReady || ui.savedAddress != null) {
                    if (ui.isReady) vm.disconnect() else vm.autoConnectLast()
                }
                .padding(horizontal = 14.dp, vertical = 5.dp),
        ) {
            Text(
                text = if (ui.isReady) "断开" else "重连",
                color = Color.White.copy(alpha = 0.90f),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    }
}

private fun statusOf(link: LinkState): Pair<String, Color> = when (link) {
    is LinkState.Ready -> "已连接  ${link.address}" to OkGreen
    is LinkState.Connecting -> "连接中 第 ${link.attempt} 次…" to WarnAmber
    is LinkState.Handshaking -> "握手中…" to WarnAmber
    is LinkState.Backoff -> "等待重连 ${link.retryInMs / 1000}s" to WarnAmber
    is LinkState.Scanning -> "正在搜索…" to Violet500
    is LinkState.Failed -> "失败：${link.reason}" to ErrRed
    LinkState.Idle -> "未连接" to Color.White.copy(alpha = 0.60f)
}
