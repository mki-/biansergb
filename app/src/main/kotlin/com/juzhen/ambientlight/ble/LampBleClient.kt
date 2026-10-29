package com.juzhen.ambientlight.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import com.juzhen.ambient.protocol.Frame
import com.juzhen.ambient.protocol.FrameStreamDecoder
import com.juzhen.ambient.protocol.LampFrame
import com.juzhen.ambient.protocol.LampState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

data class ScanHit(val address: String, val name: String?, val rssi: Int)

sealed interface LinkState {
    data object Idle : LinkState
    data object Scanning : LinkState
    data class Connecting(val address: String, val attempt: Int) : LinkState
    data class Handshaking(val address: String) : LinkState
    data class Ready(val address: String) : LinkState
    data class Backoff(val address: String, val attempt: Int, val retryInMs: Long) : LinkState
    data class Failed(val address: String, val reason: String) : LinkState
}

/**
 * Thin, purpose-built BLE transport for the ambient lamp.
 *
 * Replaces the 163-class `com.inuker.bluetooth` library the original app bundled.
 * Everything protocol-related is delegated to `:protocol`; this class only knows
 * about GATT mechanics.
 */
@SuppressLint("MissingPermission")
class LampBleClient(
    private val context: Context,
    private val scope: CoroutineScope,
) : WriteScheduler.FrameSender {

    private val appContext = context.applicationContext

    private val _state = MutableStateFlow<LinkState>(LinkState.Idle)
    val state: StateFlow<LinkState> = _state.asStateFlow()

    private val _scanResults = MutableSharedFlow<ScanHit>(extraBufferCapacity = 64)
    val scanResults: SharedFlow<ScanHit> = _scanResults.asSharedFlow()

    private val _frames = MutableSharedFlow<Frame>(extraBufferCapacity = 64)
    val frames: SharedFlow<Frame> = _frames.asSharedFlow()

    private val _deviceState = MutableSharedFlow<LampState>(extraBufferCapacity = 16)
    val deviceState: SharedFlow<LampState> = _deviceState.asSharedFlow()

    private val decoder = FrameStreamDecoder()
    private val scheduler = WriteScheduler(scope, this)

    private val adapter: BluetoothAdapter?
        get() = (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private var gatt: BluetoothGatt? = null
    private var scanning = false
    private var reconnectJob: Job? = null
    private var wantConnected = false
    private var currentAddress: String? = null
    private var consecutiveFailures = 0

    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null

    private var writeCompletion: CompletableDeferred<Boolean>? = null

    // ------------------------------------------------------------- scanning
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.scanRecord?.deviceName ?: runCatching { result.device.name }.getOrNull()
            _scanResults.tryEmit(
                ScanHit(
                    address = result.device.address,
                    name = name,
                    rssi = result.rssi,
                ),
            )
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            _state.value = LinkState.Failed("scan", "onScanFailed($errorCode)")
        }
    }

    fun startScan(timeoutMs: Long = 7_000L) {
        if (scanning) return
        if (!hasPermissions()) {
            _state.value = LinkState.Failed("", "需要蓝牙权限")
            return
        }
        val scanner = adapter?.bluetoothLeScanner ?: run {
            _state.value = LinkState.Failed("", "bluetooth not available")
            return
        }
        if (adapter?.isEnabled != true) {
            _state.value = LinkState.Failed("", "bluetooth disabled")
            return
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanning = true
        _state.value = LinkState.Scanning
        scanner.startScan(emptyList(), settings, scanCallback)
        scope.launch {
            delay(timeoutMs)
            stopScan()
        }
    }

    fun stopScan() {
        if (!scanning) return
        scanning = false
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        if (_state.value is LinkState.Scanning) _state.value = LinkState.Idle
    }

    // ------------------------------------------------------------ connection
    fun connect(address: String) {
        if (!hasPermissions()) {
            wantConnected = false
            _state.value = LinkState.Failed(address, "需要蓝牙权限")
            return
        }
        wantConnected = true
        currentAddress = address
        consecutiveFailures = 0
        reconnectJob?.cancel()
        reconnectJob = scope.launch { connectWithRetry(address) }
    }

    /**
     * GATT calls throw `SecurityException` when the runtime permission is missing,
     * so every entry point checks first (the original app crashed instead).
     */
    private fun hasPermissions(): Boolean = BlePermissions.granted(appContext)

    fun disconnect() {
        wantConnected = false
        reconnectJob?.cancel()
        reconnectJob = null
        scheduler.stop()
        closeGatt()
        _state.value = LinkState.Idle
    }

    private suspend fun connectWithRetry(address: String) {
        var attempt = 1
        while (wantConnected) {
            _state.value = LinkState.Connecting(address, attempt)
            val device = runCatching { adapter?.getRemoteDevice(address) }.getOrNull()
            if (device == null) {
                _state.value = LinkState.Failed(address, "unknown device")
                return
            }
            // create the signal *before* connecting so an early callback cannot be lost
            val signal = CompletableDeferred<Boolean>()
            connectionSignal = signal
            gatt = device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            val ok = try {
                withTimeoutOrNull(CONNECT_TIMEOUT_MS) { signal.await() } ?: false
            } finally {
                connectionSignal = null
            }
            if (ok) {
                consecutiveFailures = 0
                return
            }
            closeGatt()
            consecutiveFailures++
            attempt++
            if (!wantConnected) return
            // exponential backoff, capped — the original app retried instantly forever
            val backoff = backoffFor(consecutiveFailures)
            _state.value = LinkState.Backoff(address, consecutiveFailures, backoff)
            delay(backoff)
        }
    }

    private fun backoffFor(failures: Int): Long {
        val table = longArrayOf(1_000, 2_000, 4_000, 8_000, 15_000)
        return table[(failures - 1).coerceIn(0, table.size - 1)]
    }

    private var connectionSignal: CompletableDeferred<Boolean>? = null

    private fun closeGatt() {
        gatt?.let {
            runCatching { it.disconnect() }
            runCatching { it.close() }
        }
        gatt = null
        writeCharacteristic = null
        notifyCharacteristic = null
        decoder.reset()
        connectionSignal?.complete(false)
    }

    // ---------------------------------------------------------------- gatt cb
    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _state.value = LinkState.Handshaking(g.device.address)
                    g.discoverServices()
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    writeCharacteristic = null
                    notifyCharacteristic = null
                    decoder.reset()
                    // Unblocks connectWithRetry(), which owns the backoff/retry cycle.
                    if (connectionSignal?.isCompleted == false) {
                        connectionSignal?.complete(false)
                    }
                    if (!wantConnected) {
                        _state.value = LinkState.Idle
                    } else if (reconnectJob?.isActive != true) {
                        // A previously established link dropped and the retry loop
                        // has already exited — start a fresh backoff cycle. The
                        // isActive guard prevents two loops racing when the drop
                        // was caused by our own closeGatt().
                        reconnectJob = scope.launch {
                            currentAddress?.let { connectWithRetry(it) }
                        }
                    }
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val service = g.getService(SERVICE_UUID)
            if (status != BluetoothGatt.GATT_SUCCESS || service == null) {
                _state.value = LinkState.Failed(g.device.address, "service FFB0 not found")
                connectionSignal?.complete(false)
                return
            }
            writeCharacteristic = service.getCharacteristic(WRITE_UUID)
            notifyCharacteristic = service.getCharacteristic(NOTIFY_UUID)

            val notify = notifyCharacteristic
            if (notify == null) {
                _state.value = LinkState.Failed(g.device.address, "notify FFB2 not found")
                connectionSignal?.complete(false)
                return
            }
            g.setCharacteristicNotification(notify, true)
            val cccd = notify.getDescriptor(CCCD_UUID)
            if (cccd != null) {
                writeDescriptor(g, cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            }
            scheduler.start()
            _state.value = LinkState.Ready(g.device.address)
            connectionSignal?.complete(true)
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            handleNotification(value)
        }

        /**
         * Still delivered when the app targets an SDK < 33. Both overrides are
         * needed so notifications work on every supported Android version.
         */
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            val value = runCatching { characteristic.value }.getOrNull() ?: return
            handleNotification(value)
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            writeCompletion?.complete(status == BluetoothGatt.GATT_SUCCESS)
            writeCompletion = null
        }
    }

    private fun handleNotification(value: ByteArray) {
        for (frame in decoder.feed(value)) {
            _frames.tryEmit(frame)
            LampState.from(frame)?.let { _deviceState.tryEmit(it) }
        }
    }

    // ------------------------------------------------------------------ write
    @Suppress("DEPRECATION")
    override suspend fun write(frame: ByteArray): Boolean {
        val g = gatt ?: return false
        val ch = writeCharacteristic ?: return false
        val done = CompletableDeferred<Boolean>()
        writeCompletion = done

        val writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(ch, frame, writeType) == BluetoothGatt.GATT_SUCCESS
        } else {
            ch.writeType = writeType
            ch.value = frame
            g.writeCharacteristic(ch)
        }
        if (!started) {
            writeCompletion = null
            return false
        }
        // WRITE_TYPE_NO_RESPONSE still fires onCharacteristicWrite on most stacks,
        // but do not hang forever if it does not.
        return try {
            withTimeout(WRITE_TIMEOUT_MS) { done.await() }
        } catch (_: TimeoutCancellationException) {
            true // treat as delivered; the link layer reports real failures
        } finally {
            if (writeCompletion === done) writeCompletion = null
        }
    }

    /** Convenience: queue a framed command. */
    fun send(mergeKey: String, cmd: Int, payload: ByteArray = ByteArray(0)) {
        scheduler.submit(mergeKey, LampFrame.encode(cmd, payload))
    }

    /** Queue a raw (already framed) command, e.g. the legacy shake frames. */
    fun sendRaw(mergeKey: String, frame: ByteArray) {
        scheduler.submit(mergeKey, frame)
    }

    @Suppress("DEPRECATION")
    fun writeDescriptor(
        g: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        value: ByteArray,
    ): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        g.writeDescriptor(descriptor, value) == BluetoothGatt.GATT_SUCCESS
    } else {
        descriptor.value = value
        g.writeDescriptor(descriptor)
    }

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("0000FFB0-0000-1000-8000-00805F9B34FB")
        val WRITE_UUID: UUID = UUID.fromString("0000FFB1-0000-1000-8000-00805F9B34FB")
        val NOTIFY_UUID: UUID = UUID.fromString("0000FFB2-0000-1000-8000-00805F9B34FB")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

        /**
         * UUIDs the app also carries but never uses on the paths we could trace.
         * Kept so a future firmware/device variant can be probed automatically
         * instead of hard-coding a single pair.
         */
        val FALLBACK_SERVICE_UUID: UUID = UUID.fromString("0000FFF0-0000-1000-8000-00805F9B34FB")
        val FALLBACK_CHAR_UUID: UUID = UUID.fromString("0000FFF3-0000-1000-8000-00805F9B34FB")

        /** One connect attempt (connect + service discovery) may take this long. */
        private const val CONNECT_TIMEOUT_MS = 35_000L

        /** A no-response write still acks via callback; don't block the queue forever. */
        private const val WRITE_TIMEOUT_MS = 300L
    }
}
