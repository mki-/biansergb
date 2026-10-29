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
import androidx.compose.material3.Surface
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
import com.juzhen.ambientlight.ui.theme.LampSurface
import com.juzhen.ambientlight.ui.theme.LampSurfaceHigh
import com.juzhen.ambientlight.ui.theme.OkGreen
import com.juzhen.ambientlight.ui.theme.WarnAmber

enum class LampTab(val label: String) {
    COLOR("色彩"),
    MODE("模式"),
    DEVICE("设备"),
    ABOUT("关于"),
}

enum class LayoutMode {
    /** Phones: bottom navigation, roomy portrait canvas. */
    BottomNav,

    /** Short screens (car 800x480 at hdpi = 533x320dp): top tabs, no bottom bar. */
    TopTabs,

    /** Wide and tall enough (car 800x480 at mdpi, tablets): side rail + two panes. */
    Rail,
}

/** Below this height a bottom bar would eat too much of the screen. */
private val ShortHeight: Dp = 400.dp

/** Above this width there is room for a side rail. */
private val WideWidth: Dp = 640.dp

/**
 * Chooses the shell purely from the space Compose actually got.
 *
 * Real-world numbers this is designed against:
 *  - 800x480 @ mdpi  -> 800x480 dp  -> Rail
 *  - 800x480 @ hdpi  -> 533x320 dp  -> TopTabs (height is the binding constraint)
 *  - phone portrait  -> 360x800 dp  -> BottomNav
 *  - phone landscape -> 800x360 dp  -> TopTabs
 */
fun layoutModeFor(width: Dp, height: Dp): LayoutMode = when {
    height < ShortHeight -> LayoutMode.TopTabs
    width >= WideWidth -> LayoutMode.Rail
    else -> LayoutMode.BottomNav
}

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
        val mode = layoutModeFor(maxWidth, maxHeight)
        val short = maxHeight < ShortHeight
        val wide = mode != LayoutMode.BottomNav
        val select: (LampTab) -> Unit = { tabName = it.name }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (mode == LayoutMode.BottomNav) {
                    BottomBar(tab, select)
                }
            },
        ) { padding ->
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                if (mode == LayoutMode.Rail) {
                    SideRail(tab, select)
                }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    StatusStrip(ui, vm, compact = short)
                    if (mode == LayoutMode.TopTabs) {
                        TabStrip(tab, select, vertical = false)
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        when (tab) {
                            LampTab.COLOR -> ColorScreen(ui, vm, wide = wide)
                            LampTab.MODE -> ModeScreen(ui, vm, wide = wide, short = short)
                            LampTab.DEVICE -> DeviceScreen(ui, vm, wide = wide)
                            LampTab.ABOUT -> AboutScreen()
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ status bar
@Composable
private fun StatusStrip(ui: LampUiState, vm: LampViewModel, compact: Boolean) {
    val (text, color) = when (val link = ui.link) {
        is LinkState.Ready -> "已连接  ${link.address}" to OkGreen
        is LinkState.Connecting -> "连接中 第 ${link.attempt} 次…" to WarnAmber
        is LinkState.Handshaking -> "握手中…" to WarnAmber
        is LinkState.Backoff -> "等待重连 ${link.retryInMs / 1000}s" to WarnAmber
        is LinkState.Scanning -> "正在搜索…" to MaterialTheme.colorScheme.primary
        is LinkState.Failed -> "失败：${link.reason}" to ErrRed
        LinkState.Idle -> "未连接" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val height = if (compact) 34.dp else 44.dp
    Row(
        Modifier
            .fillMaxWidth()
            .height(height)
            .background(LampSurface)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(if (compact) 8.dp else 10.dp)
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
        if (ui.isReady) {
            TextButton(onClick = vm::disconnect) { Text("断开") }
        } else {
            TextButton(onClick = vm::autoConnectLast, enabled = ui.savedAddress != null) {
                Text("重连")
            }
        }
    }
}

// ----------------------------------------------------------------- tab strips
@Composable
private fun TabStrip(
    selected: LampTab,
    onSelect: (LampTab) -> Unit,
    vertical: Boolean,
) {
    if (vertical) {
        Column(
            Modifier
                .width(88.dp)
                .fillMaxHeight()
                .background(LampSurface)
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LampTab.entries.forEach { tab ->
                TabItem(tab, tab == selected, vertical = true) { onSelect(tab) }
            }
        }
    } else {
        Row(
            Modifier
                .fillMaxWidth()
                .background(LampSurface)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LampTab.entries.forEach { tab ->
                TabItem(tab, tab == selected, vertical = false, modifier = Modifier.weight(1f)) {
                    onSelect(tab)
                }
            }
        }
    }
}

@Composable
private fun TabItem(
    tab: LampTab,
    selected: Boolean,
    vertical: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier
            .then(if (vertical) Modifier.fillMaxWidth().height(52.dp) else Modifier.height(44.dp))
            .background(bg, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = tab.label,
            color = fg,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}

@Composable
private fun BottomBar(selected: LampTab, onSelect: (LampTab) -> Unit) {
    Surface(color = LampSurface, tonalElevation = 0.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(60.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LampTab.entries.forEach { tab ->
                TabItem(tab, tab == selected, vertical = false, modifier = Modifier.weight(1f)) {
                    onSelect(tab)
                }
            }
        }
    }
}

@Composable
private fun SideRail(selected: LampTab, onSelect: (LampTab) -> Unit) {
    Box(Modifier.background(LampSurfaceHigh)) {
        TabStrip(selected, onSelect, vertical = true)
    }
}
