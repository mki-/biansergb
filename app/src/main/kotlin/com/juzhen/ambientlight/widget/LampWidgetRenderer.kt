package com.juzhen.ambientlight.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.widget.RemoteViews
import com.juzhen.ambientlight.R

/**
 * Renders every widget instance of one layout.
 *
 * Only the views that actually exist in [WidgetSpec.layoutId] are touched: calling
 * `setOnClickPendingIntent` for a missing id makes RemoteViews throw when the
 * launcher applies it.
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

    fun render(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
        spec: WidgetSpec,
    ) {
        val powerOn = WidgetState.isPowerOn(context)

        for (widgetId in ids) {
            val views = RemoteViews(context.packageName, spec.layoutId)

            // Tap anywhere on the card -> open the app (which connects and applies nothing else).
            views.setOnClickPendingIntent(
                R.id.widget_root,
                WidgetIntents.action(context, widgetId, WidgetActions.CODE_OPEN),
            )

            // The power pill toggles the other way round.
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

            if (spec.hasState) {
                views.setTextViewText(
                    R.id.widget_state,
                    context.getString(
                        if (powerOn) R.string.widget_state_on else R.string.widget_state_off,
                    ),
                )
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

            if (spec.hasOpen) {
                views.setOnClickPendingIntent(
                    R.id.widget_open,
                    WidgetIntents.action(context, widgetId, WidgetActions.CODE_OPEN),
                )
            }

            manager.updateAppWidget(widgetId, views)
        }
    }
}
