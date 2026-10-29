package com.juzhen.ambientlight.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Entry point for every desktop-card control.
 *
 * `goAsync()` keeps the process alive for the duration of the broadcast so the short
 * BLE session can finish. The whole exchange (connect → write → disconnect) is given
 * the broadcast's ~10 s window; [QuickBleWriter][com.juzhen.ambientlight.ble.QuickBleWriter]
 * caps itself below that.
 */
class WidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WidgetActions.ACTION) return

        val appContext = context.applicationContext
        val pending = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                LampQuickAction.handle(appContext, intent)
            } catch (_: Exception) {
                // Surface the failure on the card instead of dying silently.
                WidgetState.setError(appContext, true)
                WidgetUpdater.refreshAll(appContext)
            } finally {
                pending.finish()
            }
        }
    }
}
