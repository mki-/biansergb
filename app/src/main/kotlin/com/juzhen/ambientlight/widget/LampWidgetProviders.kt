package com.juzhen.ambientlight.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import com.juzhen.ambientlight.R

/**
 * Shared plumbing: each subclass only declares which layout (and which optional views)
 * it owns, and the base class does the rendering.
 */
abstract class BaseLampWidgetProvider : AppWidgetProvider() {

    abstract val spec: WidgetSpec

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        LampWidgetRenderer.render(context, appWidgetManager, appWidgetIds, spec)
    }
}

/** 2×1 — switch only. */
class SmallLampWidgetProvider : BaseLampWidgetProvider() {
    override val spec: WidgetSpec get() = SPEC

    companion object {
        val SPEC = WidgetSpec(
            layoutId = R.layout.widget_small,
            hasColors = false,
            hasState = true,
            hasOpen = false,
        )
    }
}

/** 4×1 — switch + six swatches in one row. */
class WideLampWidgetProvider : BaseLampWidgetProvider() {
    override val spec: WidgetSpec get() = SPEC

    companion object {
        val SPEC = WidgetSpec(
            layoutId = R.layout.widget_wide,
            hasColors = true,
            hasState = false,
            hasOpen = false,
        )
    }
}

/** 2×2 — switch + 3×2 swatch grid. */
class SquareLampWidgetProvider : BaseLampWidgetProvider() {
    override val spec: WidgetSpec get() = SPEC

    companion object {
        val SPEC = WidgetSpec(
            layoutId = R.layout.widget_square,
            hasColors = true,
            hasState = false,
            hasOpen = false,
        )
    }
}

/** 4×2 — the full card. */
class BigLampWidgetProvider : BaseLampWidgetProvider() {
    override val spec: WidgetSpec get() = SPEC

    companion object {
        val SPEC = WidgetSpec(
            layoutId = R.layout.widget_big,
            hasColors = true,
            hasState = true,
            hasOpen = true,
        )
    }
}

/** 2×4 portrait card — for tall home-screen slots. */
class TallLampWidgetProvider : BaseLampWidgetProvider() {
    override val spec: WidgetSpec get() = SPEC

    companion object {
        val SPEC = WidgetSpec(
            layoutId = R.layout.widget_tall,
            hasColors = true,
            hasState = true,
            hasOpen = true,
        )
    }
}

/**
 * Re-renders every placed card. Called when the app learns a new power state, so the
 * toggle label stays honest without the widget doing BLE work itself.
 */
object WidgetUpdater {

    private data class Entry(val provider: Class<*>, val spec: WidgetSpec)

    private val entries: List<Entry> = listOf(
        Entry(SmallLampWidgetProvider::class.java, SmallLampWidgetProvider.SPEC),
        Entry(WideLampWidgetProvider::class.java, WideLampWidgetProvider.SPEC),
        Entry(SquareLampWidgetProvider::class.java, SquareLampWidgetProvider.SPEC),
        Entry(BigLampWidgetProvider::class.java, BigLampWidgetProvider.SPEC),
        Entry(TallLampWidgetProvider::class.java, TallLampWidgetProvider.SPEC),
    )

    fun refreshAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        entries.forEach { entry ->
            val ids = try {
                manager.getAppWidgetIds(ComponentName(context, entry.provider))
            } catch (_: Exception) {
                null
            }
            if (ids != null && ids.isNotEmpty()) {
                LampWidgetRenderer.render(context, manager, ids, entry.spec)
            }
        }
    }
}
