package com.juzhen.ambientlight.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewmodel.compose.viewModel
import com.juzhen.ambientlight.ble.BlePermissions
import com.juzhen.ambientlight.ui.theme.AmbientLampTheme

class MainActivity : ComponentActivity() {

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
                val vm: LampViewModel = viewModel()
                AmbientLampApp(vm)
            }
        }
    }
}
