package com.juzhen.ambientlight.ble

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Bluetooth permission split that the original app never handled: from Android 12
 * (API 31) the BLE permissions are `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT`, while
 * below that a BLE scan requires location permission.
 */
object BlePermissions {

    val required: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    /** Audio capture is used by the music-beat feature only. */
    val audio: Array<String> = arrayOf(Manifest.permission.RECORD_AUDIO)

    fun granted(context: Context, permissions: Array<String> = required): Boolean =
        permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
}
