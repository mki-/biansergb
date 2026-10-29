package com.juzhen.ambientlight.widget

import android.content.Context
import android.content.Intent
import com.juzhen.ambient.protocol.LampCommand
import com.juzhen.ambient.protocol.PowerMode
import com.juzhen.ambient.protocol.Rgb
import com.juzhen.ambientlight.ble.QuickBleWriter
import com.juzhen.ambientlight.ui.PresetColors
import com.juzhen.ambientlight.ui.hueToRgb

/**
 * Executes one desktop-card tap entirely in the background: no activity, no service,
 * no notification.
 *
 * Flow: decode the intent → build the frames → one-shot BLE session → mirror the new
 * state into [WidgetState] → redraw the cards so the toggle label stays truthful.
 */
object LampQuickAction {

    suspend fun handle(context: Context, intent: Intent) {
        val colorIndex = intent.getIntExtra(WidgetActions.EXTRA_COLOR, -1).takeIf { it >= 0 }
        val hueDegrees = intent.getIntExtra(WidgetActions.EXTRA_HUE_DEG, -1).takeIf { it >= 0 }
        val powerOn = if (intent.hasExtra(WidgetActions.EXTRA_POWER)) {
            intent.getBooleanExtra(WidgetActions.EXTRA_POWER, true)
        } else {
            null
        }

        if (colorIndex == null && hueDegrees == null && powerOn == null) return

        val frames = mutableListOf<ByteArray>()
        var forcedOn = false

        if (powerOn != null) {
            frames += LampCommand.power(if (powerOn) PowerMode.ON else PowerMode.OFF)
        }

        val target: Rgb? = when {
            colorIndex != null -> PresetColors.getOrNull(colorIndex)?.rgb
            hueDegrees != null -> hueToRgb(hueDegrees.toFloat())
            else -> null
        }

        if (target != null) {
            // Picking a colour should be visible even when the lamp was off.
            if (powerOn == null && !WidgetState.isPowerOn(context)) {
                frames += LampCommand.power(PowerMode.ON)
                forcedOn = true
            }
            frames += LampCommand.rgb(target, target)
        }

        val ok = QuickBleWriter(context).send(PinnedLamp.mac, frames)

        when {
            powerOn != null -> WidgetState.setPowerOn(context, powerOn)
            ok && forcedOn -> WidgetState.setPowerOn(context, true)
        }
        WidgetState.setError(context, !ok)
        WidgetUpdater.refreshAll(context)
    }
}
