package com.juzhen.ambientlight.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.widget.RemoteViews
import com.juzhen.ambientlight.R

/**
 * Renders every widget instance of one layout.
 *
 * Only the views that actually exist in [WidgetSpec.layoutId] are touched: calling
 * `setOnClickPendingIntent` for a missing id makes RemoteViews throw when the launcher
 * applies it, so every optional control is guarded by a spec flag.
 *
 * Controls fire broadcasts ([WidgetIntents.action]) so the lamp is driven in the
 * background; only the card background opens the app.
 */
object LampWidgetRenderer {

    private val COLOR_VIEW_IDS = intArrayOf(
        R.id.widget_color_0,
        R.id.widget_color_1,
        R.id.widget_color_2,
        R.id.widget_color_3,
        R.id.widget_color_4,
        R.id.widget_color_5,
    )

    private val HUE_VIEW_IDS = intArrayOf(
        R.id.widget_hue_0,
        R.id.widget_hue_1,
        R.id.widget_hue_2,
        R.id.widget_hue_3,
        R.id.widget_hue_4,
        R.id.widget_hue_5,
    )

    fun render(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
        spec: WidgetSpec,
    ) {
        val powerOn = WidgetState.isPowerOn(context)
        val failed = WidgetState.hasError(context)

        for (widgetId in ids) {
            val views = RemoteViews(context.packageName, spec.layoutId)

            // Background tap -> open the app. Everything else stays in the launcher.
            views.setOnClickPendingIntent(R.id.widget_root, WidgetIntents.openApp(context, widgetId))

            // Round power button: toggles the opposite of the known state.
            views.setOnClickPendingIntent(
                R.id.widget_power,
                WidgetIntents.action(
                    context,
                    widgetId,
                    WidgetActions.CODE_POWER,
                    powerOn = !powerOn,
                ),
            )
            views.setTextViewText(
                R.id.widget_power,
                context.getString(if (powerOn) R.string.widget_on else R.string.widget_off),
            )
            views.setInt(
                R.id.widget_power,
                "setBackgroundResource",
                if (powerOn) R.drawable.widget_circle_on else R.drawable.widget_circle_off,
            )

            if (spec.hasState) {
                val stateText = when {
                    failed -> R.string.widget_state_error
                    powerOn -> R.string.widget_state_on
                    else -> R.string.widget_state_off
                }
                views.setTextViewText(R.id.widget_state, context.getString(stateText))
            }

            if (spec.hasColors) {
                WidgetActions.PALETTE.forEachIndexed { index, presetIndex ->
                    views.setOnClickPendingIntent(
                        COLOR_VIEW_IDS[index],
                        WidgetIntents.action(
                            context,
                            widgetId,
                            WidgetActions.CODE_COLOR_BASE + index,
                            colorIndex = presetIndex,
                        ),
                    )
                }
            }

            spec.hueSteps.forEachIndexed { index, degrees ->
                if (index < HUE_VIEW_IDS.size) {
                    views.setOnClickPendingIntent(
                        HUE_VIEW_IDS[index],
                        WidgetIntents.action(
                            context,
                            widgetId,
                            WidgetActions.CODE_HUE_BASE + index,
                            hueDegrees = degrees,
                        ),
                    )
                }
            }

            if (spec.hasOpen) {
                views.setOnClickPendingIntent(R.id.widget_open, WidgetIntents.openApp(context, widgetId))
            }

            manager.updateAppWidget(widgetId, views)
        }
    }
}
