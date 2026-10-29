package com.juzhen.ambientlight.ui

import androidx.compose.ui.graphics.Color
import com.juzhen.ambient.protocol.Rgb

/** Bridge between the protocol's RGB model and Compose. */
fun Rgb.toComposeColor(): Color = Color(red, green, blue)
