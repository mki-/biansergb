package com.juzhen.ambientlight.widget

import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import com.juzhen.ambientlight.BuildConfig
import com.juzhen.ambientlight.ui.MainActivity

/**
 * The lamp this build is paired with. Configured through `ambient.pinnedMac` in
 * gradle.properties (exposed as `BuildConfig.PINNED_MAC`), so a different batch of
 * hardware only needs a build-config change, not a code change.
 *
 * Every desktop-card tap auto-connects to this address.
 */
object PinnedLamp {
    private const val DEFAULT_MAC: String = "FF:FF:10:98:3C:D9"

    val mac: String = BuildConfig.PINNED_MAC.ifBlank { DEFAULT_MAC }
}

/** Which views a given widget layout actually contains. */
data class WidgetSpec(
    val layoutId: Int,
    val hasColors: Boolean,
    val hasState: Boolean,
    val hasOpen: Boolean,
)

object WidgetActions {
    const val ACTION = "com.juzhen.ambientlight.action.WIDGET"
    const val EXTRA_FROM_WIDGET = "widget_from"
    const val EXTRA_COLOR = "widget_color_index"
    const val EXTRA_POWER = "widget_power_on"

    /* request-code offsets; must stay stable so PendingIntents keep their identity */
    const val CODE_OPEN = 0
    const val CODE_POWER = 1
    const val CODE_COLOR_BASE = 2

    /**
     * The six swatches on the card, as indices into [com.juzhen.ambientlight.ui.PresetColors]:
     * 正白, 桃粉, 大红, 紫罗兰, 正蓝, 青色.
     */
    val PALETTE: List<Int> = listOf(2, 13, 9, 16, 20, 24)

    val COLOR_NAMES: List<String> = listOf("白色", "粉色", "红色", "紫色", "蓝色", "青色")
}

/**
 * Last known power state, mirrored into shared preferences so the card can render a
 * sensible toggle label without a live BLE connection.
 */
object WidgetState {
    private const val PREFS = "ambient_widget"
    private const val KEY_POWER = "power_on"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isPowerOn(context: Context): Boolean = prefs(context).getBoolean(KEY_POWER, true)

    fun setPowerOn(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_POWER, on).apply()
    }
}

/**
 * Builds the PendingIntents the card's views fire.
 *
 * Everything goes through [MainActivity] (not a background service): the activity is
 * already the single owner of the BLE connection, so tapping a card opens the app and
 * the ViewModel connects to [PinnedLamp.mac] and applies the requested action. This
 * keeps the widget free of foreground-service plumbing and its permission surface.
 */
object WidgetIntents {

    fun action(
        context: Context,
        widgetId: Int,
        code: Int,
        colorIndex: Int? = null,
        powerOn: Boolean? = null,
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = WidgetActions.ACTION
            putExtra(WidgetActions.EXTRA_FROM_WIDGET, true)
            if (colorIndex != null) putExtra(WidgetActions.EXTRA_COLOR, colorIndex)
            if (powerOn != null) putExtra(WidgetActions.EXTRA_POWER, powerOn)
        }
        val requestCode = widgetId * 16 + code
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
