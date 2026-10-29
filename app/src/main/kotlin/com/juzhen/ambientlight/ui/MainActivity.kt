package com.juzhen.ambientlight.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.juzhen.ambientlight.ble.BlePermissions
import com.juzhen.ambientlight.ui.theme.AmbientLampTheme
import com.juzhen.ambientlight.widget.WidgetActions

class MainActivity : ComponentActivity() {

    private val vm: LampViewModel by viewModels()

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ask before the first composition: GATT calls throw SecurityException when
        // the runtime permission is missing (the original app crashed instead).
        val needed = BlePermissions.required
        if (!BlePermissions.granted(this, needed)) {
            requestPermissions.launch(needed)
        }

        setContent {
            AmbientLampTheme {
                AmbientLampApp(vm)
            }
        }

        handleWidgetIntent(intent)
    }

    public override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWidgetIntent(intent)
    }

    /**
     * A desktop card tap arrives here. The card only carries what to do (a preset index
     * or a power state); connecting to the paired lamp is the ViewModel's job, so the
     * widget needs no foreground service of its own.
     */
    private fun handleWidgetIntent(intent: Intent?) {
        if (intent == null || intent.action != WidgetActions.ACTION) return

        val color = if (intent.hasExtra(WidgetActions.EXTRA_COLOR)) {
            intent.getIntExtra(WidgetActions.EXTRA_COLOR, -1).takeIf { it >= 0 }
        } else {
            null
        }
        val power = if (intent.hasExtra(WidgetActions.EXTRA_POWER)) {
            intent.getBooleanExtra(WidgetActions.EXTRA_POWER, true)
        } else {
            null
        }

        vm.onWidgetAction(colorIndex = color, powerOn = power)
    }
}
