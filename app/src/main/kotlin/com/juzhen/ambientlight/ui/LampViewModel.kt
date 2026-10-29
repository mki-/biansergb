package com.juzhen.ambientlight.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.juzhen.ambient.protocol.AsciiReport
import com.juzhen.ambient.protocol.LampCommand
import com.juzhen.ambient.protocol.LampState
import com.juzhen.ambient.protocol.PowerMode
import com.juzhen.ambient.protocol.Rgb
import com.juzhen.ambient.protocol.Zone
import com.juzhen.ambientlight.ble.LampBleClient
import com.juzhen.ambientlight.ble.LinkState
import com.juzhen.ambientlight.ble.ScanHit
import com.juzhen.ambientlight.data.DeviceStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LampUiState(
    val link: LinkState = LinkState.Idle,
    val hits: List<ScanHit> = emptyList(),
    val savedAddress: String? = null,
    val zone: Zone = Zone.ALL,
    val hue: Float = 0f,
    val saturation: Float = 1f,
    val brightness: Int = 100,
    val power: PowerMode = PowerMode.ON,
    val speed: Int = 3,
    val ledCount: Int = 60,
    val micSensitivity: Int = 50,
    val reverse: Boolean = false,
    val rotationFollow: Boolean = false,
    val flowFamily: Int = 0,
    val deviceState: LampState? = null,
    val reports: List<String> = emptyList(),
    val message: String? = null,
) {
    val color: Rgb get() = hsvToRgb(hue, saturation)
    val isReady: Boolean get() = link is LinkState.Ready
}

class LampViewModel(app: Application) : AndroidViewModel(app) {

    private val ble = LampBleClient(app, viewModelScope)
    private val store = DeviceStore(app)

    /** True once the first full state dump of the current connection was adopted. */
    private var stateSynced = false

    private val _ui = MutableStateFlow(
        LampUiState(
            savedAddress = store.lastAddress,
            zone = Zone.entries.getOrElse(store.zone) { Zone.ALL },
            brightness = store.brightness,
            hue = rgbToHue(store.lastColor),
        ),
    )
    val ui: StateFlow<LampUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            ble.state.collect { link ->
                _ui.update { it.copy(link = link) }
                if (link is LinkState.Ready) {
                    // handshake: identify the device, then ask for a full state dump
                    ble.sendRaw(KEY_INFO, LampCommand.info())
                    ble.sendRaw(KEY_STATE, LampCommand.status())
                } else {
                    stateSynced = false
                }
            }
        }
        viewModelScope.launch {
            ble.scanResults.collect { hit ->
                _ui.update { state ->
                    val merged = state.hits.filterNot { it.address == hit.address } + hit
                    state.copy(hits = merged.sortedByDescending { it.rssi }.take(MAX_HITS))
                }
            }
        }
        viewModelScope.launch {
            ble.frames.collect { frame ->
                val state = LampState.from(frame)
                if (state != null) {
                    // Only adopt the device's brightness on the first dump after
                    // connecting — otherwise a status poll would yank the slider
                    // back while the user is still dragging it.
                    val adoptBrightness = !stateSynced
                    stateSynced = true
                    _ui.update {
                        it.copy(
                            deviceState = state,
                            power = if (state.power) PowerMode.ON else PowerMode.OFF,
                            brightness = if (adoptBrightness) state.brightness1 else it.brightness,
                        )
                    }
                } else {
                    val text = AsciiReport.decode(frame)
                    if (isPrintableReport(text)) {
                        _ui.update { s ->
                            s.copy(reports = (listOf(text) + s.reports).distinct().take(MAX_REPORTS))
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ link
    fun startScan() = ble.startScan()
    fun stopScan() = ble.stopScan()

    fun connect(address: String) {
        ble.stopScan()
        store.lastAddress = address
        _ui.update { it.copy(savedAddress = address, reports = emptyList()) }
        ble.connect(address)
    }

    fun autoConnectLast() {
        val address = store.lastAddress ?: return
        if (_ui.value.isReady) return
        ble.connect(address)
    }

    fun disconnect() {
        ble.disconnect()
        _ui.update { it.copy(link = LinkState.Idle) }
    }

    // -------------------------------------------------------------- controls
    fun setZone(zone: Zone) {
        store.zone = zone.ordinal
        _ui.update { it.copy(zone = zone) }
    }

    fun setHue(hue: Float) {
        _ui.update { it.copy(hue = hue) }
        pushColor()
    }

    fun setPreset(rgb: Rgb) {
        _ui.update { it.copy(hue = rgbToHue(rgb), saturation = 1f) }
        pushColor()
    }

    fun setBrightness(value: Int) {
        val clamped = value.coerceIn(0, LampCommand.BRIGHTNESS_MAX)
        store.brightness = clamped
        _ui.update { it.copy(brightness = clamped) }
        pushBrightness()
    }

    fun setPower(mode: PowerMode) = send(KEY_POWER, LampCommand.power(mode)) {
        _ui.update { it.copy(power = mode) }
    }

    fun setSpeed(value: Int) = send(KEY_SPEED, LampCommand.speed(value.coerceIn(0, LampCommand.SPEED_MAX))) {
        _ui.update { it.copy(speed = value.coerceIn(0, LampCommand.SPEED_MAX)) }
    }

    fun setLedCount(value: Int) =
        send(KEY_LED, LampCommand.ledCount(value.coerceIn(0, LampCommand.LED_COUNT_MAX))) {
            _ui.update { it.copy(ledCount = value.coerceIn(0, LampCommand.LED_COUNT_MAX)) }
        }

    fun setMicSensitivity(value: Int) =
        send(KEY_MIC, LampCommand.micSensitivity(value.coerceIn(0, LampCommand.MIC_SENSITIVITY_MAX))) {
            _ui.update { it.copy(micSensitivity = value.coerceIn(0, LampCommand.MIC_SENSITIVITY_MAX)) }
        }

    fun setDirection(reverse: Boolean) = send(KEY_DIR, LampCommand.direction(reverse)) {
        _ui.update { it.copy(reverse = reverse) }
    }

    fun setRotationFollow(on: Boolean) = send(KEY_ROT, LampCommand.rotationFollow(on)) {
        _ui.update { it.copy(rotationFollow = on) }
    }

    fun selectFlowFamily(index: Int) {
        _ui.update { it.copy(flowFamily = index.coerceIn(0, 4)) }
    }

    fun sendFlowStep(step: Int) {
        val family = _ui.value.flowFamily
        send(KEY_FLOW, LampCommand.flow(family, step))
    }

    fun sendBuiltinMode() = send(KEY_SYS_MODE, LampCommand.builtinMode())

    fun sendRfPair() = send(KEY_SYS_RF, LampCommand.rfPair())

    fun refreshState() = send(KEY_STATE, LampCommand.status())

    // --------------------------------------------------------------- message
    fun notify(message: String) = _ui.update { it.copy(message = message) }

    fun clearMessage() = _ui.update { it.copy(message = null) }

    // --------------------------------------------------------------- helpers
    private fun pushColor() {
        val state = _ui.value
        val current = state.color
        // The frame always carries both zones; the untouched zone keeps its colour.
        val other = when (state.zone) {
            Zone.ZONE_1 -> state.deviceState?.zone2 ?: Rgb.WHITE
            Zone.ZONE_2 -> state.deviceState?.zone1 ?: Rgb.WHITE
            Zone.ALL -> current
        }
        val (z1, z2) = when (state.zone) {
            Zone.ZONE_1 -> current to other
            Zone.ZONE_2 -> other to current
            Zone.ALL -> current to current
        }
        store.lastColor = z1
        send(KEY_COLOR, LampCommand.rgb(z1, z2))
    }

    private fun pushBrightness() {
        val state = _ui.value
        val value = state.brightness
        val other = when (state.zone) {
            Zone.ZONE_1 -> state.deviceState?.brightness2 ?: 100
            Zone.ZONE_2 -> state.deviceState?.brightness1 ?: 100
            Zone.ALL -> value
        }
        val (b1, b2) = when (state.zone) {
            Zone.ZONE_1 -> value to other
            Zone.ZONE_2 -> other to value
            Zone.ALL -> value to value
        }
        send(KEY_BRIGHTNESS, LampCommand.brightness(b1, b2))
    }

    /**
     * Control actions stay interactive while disconnected so the UI does not feel
     * dead; they simply tell the user to connect instead of silently dropping frames.
     */
    private inline fun send(mergeKey: String, frame: ByteArray, onApplied: () -> Unit = {}) {
        if (!_ui.value.isReady) {
            _ui.update { it.copy(message = "请先连接设备") }
            return
        }
        onApplied()
        ble.sendRaw(mergeKey, frame)
    }

    private fun isPrintableReport(text: String): Boolean =
        text.isNotBlank() && text.length in 2..48 &&
            text.all { it.isLetterOrDigit() || it in " .-_/:+()#%," }

    private companion object {
        const val KEY_INFO = "info"
        const val KEY_STATE = "state"
        const val KEY_COLOR = "color"
        const val KEY_BRIGHTNESS = "brightness"
        const val KEY_POWER = "power"
        const val KEY_SPEED = "speed"
        const val KEY_LED = "ledCount"
        const val KEY_MIC = "mic"
        const val KEY_DIR = "direction"
        const val KEY_ROT = "rotFollow"
        const val KEY_FLOW = "flow"
        const val KEY_SYS_MODE = "sysMode"
        const val KEY_SYS_RF = "sysRf"
        const val MAX_HITS = 24
        const val MAX_REPORTS = 6
    }
}
