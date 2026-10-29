package com.juzhen.ambientlight.ui

import com.juzhen.ambient.protocol.Rgb

/** A named colour preset shown in the swatch grid. */
data class Preset(val name: String, val rgb: Rgb)

/**
 * 32 mainstream colours, ordered so an 8x4 grid reads naturally:
 *
 *  row 1  whites / warm white → amber
 *  row 2  orange / red / pink family
 *  row 3  violet / blue family
 *  row 4  cyan / green family
 *
 * The lamp is an RGB addressable strip, so a curated palette is more useful in a
 * car than a hue wheel: every target is one tap away and needs no precision drag
 * while driving.
 */
val PresetColors: List<Preset> = listOf(
    // --- row 1: white / warm ------------------------------------------------
    Preset("暖白", Rgb(255, 214, 170)),
    Preset("自然白", Rgb(255, 241, 224)),
    Preset("正白", Rgb(255, 255, 255)),
    Preset("冷白", Rgb(238, 245, 255)),
    Preset("米黄", Rgb(255, 240, 200)),
    Preset("琥珀", Rgb(255, 191, 0)),
    Preset("金黄", Rgb(255, 215, 0)),
    Preset("橘黄", Rgb(255, 140, 0)),

    // --- row 2: orange / red / pink ----------------------------------------
    Preset("珊瑚", Rgb(255, 99, 71)),
    Preset("大红", Rgb(255, 0, 0)),
    Preset("中国红", Rgb(200, 20, 20)),
    Preset("酒红", Rgb(139, 0, 0)),
    Preset("玫红", Rgb(255, 20, 147)),
    Preset("桃粉", Rgb(255, 105, 180)),
    Preset("樱花粉", Rgb(255, 182, 193)),
    Preset("紫红", Rgb(199, 21, 133)),

    // --- row 3: violet / blue ----------------------------------------------
    Preset("紫罗兰", Rgb(138, 43, 226)),
    Preset("正紫", Rgb(128, 0, 128)),
    Preset("深紫", Rgb(75, 0, 130)),
    Preset("靛蓝", Rgb(75, 0, 180)),
    Preset("正蓝", Rgb(0, 0, 255)),
    Preset("宝蓝", Rgb(0, 71, 171)),
    Preset("天蓝", Rgb(0, 150, 255)),
    Preset("青蓝", Rgb(0, 191, 255)),

    // --- row 4: cyan / green -----------------------------------------------
    Preset("青色", Rgb(0, 255, 255)),
    Preset("水绿", Rgb(64, 224, 208)),
    Preset("薄荷绿", Rgb(120, 240, 190)),
    Preset("正绿", Rgb(0, 255, 0)),
    Preset("翠绿", Rgb(0, 200, 83)),
    Preset("森林绿", Rgb(0, 128, 0)),
    Preset("青柠", Rgb(173, 255, 47)),
    Preset("黄绿", Rgb(154, 205, 50)),
)

/** Index of the preset closest to [rgb] — used to restore the last colour. */
fun nearestPreset(rgb: Rgb): Int {
    var best = 0
    var bestDist = Long.MAX_VALUE
    PresetColors.forEachIndexed { index, preset ->
        val dr = (preset.rgb.red - rgb.red).toLong()
        val dg = (preset.rgb.green - rgb.green).toLong()
        val db = (preset.rgb.blue - rgb.blue).toLong()
        val dist = dr * dr + dg * dg + db * db
        if (dist < bestDist) {
            bestDist = dist
            best = index
        }
    }
    return best
}
