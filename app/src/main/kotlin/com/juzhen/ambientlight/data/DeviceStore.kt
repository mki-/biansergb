package com.juzhen.ambientlight.data

import android.content.Context
import com.juzhen.ambient.protocol.Rgb

/**
 * Tiny preferences wrapper: the app only needs the last lamp, the last zone and the
 * last colour. (The original app used a full SQLite ORM for this.)
 */
class DeviceStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("ambient_lamp", Context.MODE_PRIVATE)

    var lastAddress: String?
        get() = prefs.getString(KEY_ADDRESS, null)
        set(value) = prefs.edit().putString(KEY_ADDRESS, value).apply()

    var lastName: String?
        get() = prefs.getString(KEY_NAME, null)
        set(value) = prefs.edit().putString(KEY_NAME, value).apply()

    /** Stored as `Zone.ordinal`: ZONE_1=0, ZONE_2=1, ALL=2. */
    var zone: Int
        get() = prefs.getInt(KEY_ZONE, DEFAULT_ZONE)
        set(value) = prefs.edit().putInt(KEY_ZONE, value).apply()

    var brightness: Int
        get() = prefs.getInt(KEY_BRIGHTNESS, 100)
        set(value) = prefs.edit().putInt(KEY_BRIGHTNESS, value).apply()

    var lastColor: Rgb
        get() = Rgb(
            prefs.getInt(KEY_R, 255).coerceIn(0, 255),
            prefs.getInt(KEY_G, 255).coerceIn(0, 255),
            prefs.getInt(KEY_B, 255).coerceIn(0, 255),
        )
        set(value) = prefs.edit()
            .putInt(KEY_R, value.red)
            .putInt(KEY_G, value.green)
            .putInt(KEY_B, value.blue)
            .apply()

    private companion object {
        const val KEY_ADDRESS = "last_address"
        const val KEY_NAME = "last_name"
        const val KEY_ZONE = "zone"
        const val KEY_BRIGHTNESS = "brightness"
        const val KEY_R = "color_r"
        const val KEY_G = "color_g"
        const val KEY_B = "color_b"
        const val DEFAULT_ZONE = 2
    }
}
