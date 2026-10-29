package com.juzhen.ambientlight.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One-shot BLE session: connect to a known MAC, discover the service, write frames,
 * disconnect.
 *
 * This exists so a desktop card can change the light **without launching the app**.
 * It deliberately has none of [LampBleClient]'s machinery (state machine, reconnect
 * backoff, write scheduler) because it must finish inside a broadcast's async window.
 *
 * Connecting to a known MAC needs no scan, so this works from the background; only
 * scanning is restricted there.
 */
class QuickBleWriter(context: Context) {

    private val appContext = context.applicationContext

    suspend fun send(
        mac: String,
        frames: List<ByteArray>,
        timeoutMs: Long = 9_000L,
    ): Boolean {
        if (frames.isEmpty()) return true

        val adapter = (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            ?: return false
        if (!adapter.isEnabled) return false

        val device = runCatching { adapter.getRemoteDevice(mac) }.getOrNull() ?: return false

        val ready = CompletableDeferred<BluetoothGattCharacteristic>()
        val ack = Channel<Boolean>(Channel.CONFLATED)
        var gatt: BluetoothGatt? = null

        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    g.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED && !ready.isCompleted) {
                    ready.completeExceptionally(IllegalStateException("disconnected"))
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                val characteristic = g.getService(LampBleClient.SERVICE_UUID)
                    ?.getCharacteristic(LampBleClient.WRITE_UUID)
                if (status == BluetoothGatt.GATT_SUCCESS && characteristic != null) {
                    ready.complete(characteristic)
                } else if (!ready.isCompleted) {
                    ready.completeExceptionally(IllegalStateException("service FFB0 missing"))
                }
            }

            override fun onCharacteristicWrite(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                ack.trySend(status == BluetoothGatt.GATT_SUCCESS)
            }
        }

        val ok = withTimeoutOrNull(timeoutMs) {
            try {
                gatt = device.connectGatt(appContext, false, callback, BluetoothDevice.TRANSPORT_LE)
                val characteristic = ready.await()
                for (frame in frames) {
                    if (!writeFrame(gatt, characteristic, frame)) return@withTimeoutOrNull false
                    // WRITE_TYPE_NO_RESPONSE still acks; don't block forever if it does not
                    val acked = withTimeoutOrNull(600L) { ack.receive() }
                    if (acked == false) return@withTimeoutOrNull false
                    delay(120L)
                }
                true
            } catch (_: Exception) {
                false
            }
        } ?: false

        runCatching { gatt?.disconnect() }
        runCatching { gatt?.close() }
        return ok
    }

    @Suppress("DEPRECATION")
    private fun writeFrame(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic,
        frame: ByteArray,
    ): Boolean {
        if (gatt == null) return false
        val type = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(characteristic, frame, type) == BluetoothGatt.GATT_SUCCESS
        } else {
            characteristic.writeType = type
            characteristic.value = frame
            gatt.writeCharacteristic(characteristic)
        }
    }
}
