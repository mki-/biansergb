package com.juzhen.ambient.protocol

/** Power state as reported by / sent to the device. Note the *non-obvious* values. */
enum class PowerMode(val wire: Int) {
    OFF(0),
    FOLLOW(1),   // 跟随 — reacts to the device microphone
    ON(2);

    companion object {
        fun fromWire(value: Int): PowerMode = when (value) {
            0 -> OFF
            1 -> FOLLOW
            else -> ON
        }
    }
}

/** Which zone a command applies to. */
enum class Zone { ZONE_1, ZONE_2, ALL }

data class Rgb(val red: Int, val green: Int, val blue: Int) {
    init {
        require(red in 0..255 && green in 0..255 && blue in 0..255) { "rgb out of range: $this" }
    }

    companion object {
        val WHITE = Rgb(255, 255, 255)
        val BLACK = Rgb(0, 0, 0)
    }
}

/**
 * Full device snapshot, parsed from a `55 0E 00 ...` frame.
 *
 * Payload layout (13 bytes), indices are payload-relative:
 * ```
 *  0 brightness1   1 mode1   2 red1   3 green1   4 blue1
 *  5 brightness2   6 mode2   7 red2   8 green2   9 blue2
 * 10 power (0 = off, else on)
 * 11 microphone level
 * 12 unused
 * ```
 */
data class LampState(
    val brightness1: Int,
    val mode1: Int,
    val zone1: Rgb,
    val brightness2: Int,
    val mode2: Int,
    val zone2: Rgb,
    val power: Boolean,
    val micLevel: Int,
    val reserved: Int = 0,
) {
    fun brightness(zone: Zone): Int = when (zone) {
        Zone.ZONE_1 -> brightness1
        Zone.ZONE_2 -> brightness2
        Zone.ALL -> (brightness1 + brightness2) / 2
    }

    fun color(zone: Zone): Rgb = when (zone) {
        Zone.ZONE_1 -> zone1
        Zone.ZONE_2 -> zone2
        Zone.ALL -> zone1
    }

    companion object {
        const val CMD_STATE: Int = 0x00
        const val STATE_PAYLOAD_SIZE: Int = 13

        /** Returns null when [frame] is not a full-state frame. */
        fun from(frame: Frame): LampState? {
            if (frame.cmd != CMD_STATE) return null
            if (frame.payload.size < 12) return null
            val p = frame.payload
            fun u(i: Int) = p[i].toInt() and 0xFF
            return LampState(
                brightness1 = u(0),
                mode1 = u(1),
                zone1 = Rgb(u(2), u(3), u(4)),
                brightness2 = u(5),
                mode2 = u(6),
                zone2 = Rgb(u(7), u(8), u(9)),
                power = u(10) != 0,
                micLevel = u(11),
                reserved = if (p.size > 12) u(12) else 0,
            )
        }
    }
}

/**
 * ASCII report frames (firmware version / model / working state).
 *
 * The original app builds `new String(bytes)` then drops the last byte, using the
 * platform default charset. We use UTF-8 and cut at the first NUL, which is
 * strictly more robust for the same traffic.
 */
object AsciiReport {
    fun decode(frame: Frame): String {
        if (frame.payload.isEmpty()) return ""
        // The app does `new String(bytes)` then `substring(0, length - 1)`, i.e. it
        // drops exactly ONE trailing byte (the firmware's terminator). We do the
        // same and then additionally strip NUL padding, which is harmless for the
        // same traffic and safer for shorter/longer reports.
        var end = frame.payload.size - 1
        while (end > 0 && frame.payload[end - 1].toInt() == 0) end--
        return String(frame.payload, 0, end, Charsets.UTF_8).trim()
    }
}
