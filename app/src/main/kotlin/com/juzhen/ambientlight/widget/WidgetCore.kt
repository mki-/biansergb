package com.juzhen.ambientlight.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.juzhen.ambientlight.BuildConfig
import com.juzhen.ambientlight.ui.MainActivity

/**
 * The lamp this build is paired with. Configured through `ambient.pinnedMac` in
 * gradle.properties (exposed as `BuildConfig.PINNED_MAC`), so a different batch of
 * hardware only needs a build-config change, not a code change.
 *
 * Every desktop-card control talks to this address directly, in the background.
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
    /**
     * Hues (degrees) of the segmented colour strip, one entry per segment.
     * Empty when the layout has no strip.
     */
    val hueSteps: List<Int> = emptyList(),
)

object WidgetActions {
    const val ACTION = "com.juzhen.ambientlight.action.WIDGET"
    const val EXTRA_COLOR = "widget_color_index"
    const val EXTRA_HUE_DEG = "widget_hue_degrees"
    const val EXTRA_POWER = "widget_power_on"

    /* request-code offsets; stable so PendingIntents keep their identity */
    const val CODE_OPEN = 0
    const val CODE_POWER = 1
    const val CODE_COLOR_BASE = 2
    const val CODE_HUE_BASE = 8

    /**
     * The six fixed swatches, as indices into [com.juzhen.ambientlight.ui.PresetColors]:
     * 正白, 桃粉, 大红, 紫罗兰, 正蓝, 青色.
     */
    val PALETTE: List<Int> = listOf(2, 13, 9, 16, 20, 24)

    /**
     * Hues of the tall card's segmented colour strip. Six segments at 60° each map
     * onto `widget_hue_0/2/4/6/8/10`, i.e. red → yellow → green → cyan → blue → magenta.
     * A real draggable slider is impossible in a home-screen widget: RemoteViews has
     * no SeekBar, so the strip is a gradient bar with tappable segments.
     */
    val STRIP_HUES: List<Int> = listOf(0, 60, 120, 180, 240, 300)
}

/**
 * Last known power state plus the outcome of the last card action, mirrored into
 * shared preferences so a card can render honestly without a live connection.
 */
object WidgetState {
    private const val PREFS = "ambient_widget"
    private const val KEY_POWER = "power_on"
    private const val KEY_ERROR = "last_error"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isPowerOn(context: Context): Boolean = prefs(context).getBoolean(KEY_POWER, true)

    fun setPowerOn(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_POWER, on).apply()
    }

    fun hasError(context: Context): Boolean = prefs(context).getBoolean(KEY_ERROR, false)

    fun setError(context: Context, error: Boolean) {
        prefs(context).edit().putBoolean(KEY_ERROR, error).apply()
    }
}

/**
 * Builds the PendingIntents the card's views fire.
 *
 * Controls use **broadcasts**, not the activity: a tap must connect and write to the
 * lamp without pulling the app to the foreground. [WidgetActionReceiver] runs the
 * short-lived BLE session. Only the card background opens the app.
 */
object WidgetIntents {

    fun openApp(context: Context, widgetId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = WidgetActions.ACTION
        }
        return PendingIntent.getActivity(
            context,
            widgetId * 64 + WidgetActions.CODE_OPEN,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun action(
        context: Context,
        widgetId: Int,
        code: Int,
        colorIndex: Int? = null,
        hueDegrees: Int? = null,
        powerOn: Boolean? = null,
    ): PendingIntent {
        val intent = Intent(context, WidgetActionReceiver::class.java).apply {
            action = WidgetActions.ACTION
            if (colorIndex != null) putExtra(WidgetActions.EXTRA_COLOR, colorIndex)
            if (hueDegrees != null) putExtra(WidgetActions.EXTRA_HUE_DEG, hueDegrees)
            if (powerOn != null) putExtra(WidgetActions.EXTRA_POWER, powerOn)
        }
        return PendingIntent.getBroadcast(
            context,
            widgetId * 64 + code,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
