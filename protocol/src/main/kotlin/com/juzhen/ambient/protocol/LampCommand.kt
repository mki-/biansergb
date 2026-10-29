package com.juzhen.ambient.protocol

/**
 * Every command the firmware understands, with the exact payload encoding.
 * See `docs/02-ble-protocol-spec.md` for the reverse-engineered tables.
 */
object LampCommand {

    const val CMD_INFO = 0x00
    const val CMD_BRIGHTNESS = 0x02
    const val CMD_MODE = 0x03
    const val CMD_RGB = 0x04
    const val CMD_POWER = 0x05
    const val CMD_MIC_SENSITIVITY = 0x06
    const val CMD_STATUS_A = 0x10
    const val CMD_STATUS_B = 0x11
    const val CMD_STATUS_C = 0x12
    const val CMD_SYSTEM = 0x16
    const val CMD_FLOW = 0x22
    const val CMD_SPEED = 0x23
    const val CMD_DIRECTION = 0x24
    const val CMD_ROT_FOLLOW = 0x25
    const val CMD_LED_COUNT = 0x26

    const val BRIGHTNESS_MAX = 100
    const val SPEED_MAX = 5
    const val LED_COUNT_MAX = 150
    const val MIC_SENSITIVITY_MAX = 100

    const val SYSTEM_BUILTIN_MODE = 0x01
    const val SYSTEM_RF_PAIR = 0x03

    fun info(): ByteArray = LampFrame.encode(CMD_INFO)

    fun status(code: Int = CMD_STATUS_B): ByteArray {
        require(code == CMD_STATUS_A || code == CMD_STATUS_B || code == CMD_STATUS_C)
        return LampFrame.encode(code, byteArrayOf(0x00))
    }

    fun brightness(zone1: Int, zone2: Int): ByteArray {
        require(zone1 in 0..BRIGHTNESS_MAX) { "zone1 brightness $zone1" }
        require(zone2 in 0..BRIGHTNESS_MAX) { "zone2 brightness $zone2" }
        return LampFrame.encode(CMD_BRIGHTNESS, byteArrayOf(b(zone1), b(zone2)))
    }

    /**
     * Brightness for one zone while preserving the other.
     *
     * The wire format always carries *both* zones, so [otherBrightness] must be
     * the other zone's current value — passing 0 here would switch that zone off.
     */
    fun brightness(zone: Zone, value: Int, otherBrightness: Int): ByteArray = when (zone) {
        Zone.ZONE_1 -> brightness(value, otherBrightness)
        Zone.ZONE_2 -> brightness(otherBrightness, value)
        Zone.ALL -> brightness(value, value)
    }

    /**
     * 6 bytes: `r1 g1 b1 r2 g2 b2`.
     *
     * Caveat inherited from the firmware/app: an all-zero payload is rejected by
     * the original app, so pure black cannot be expressed here — use [power] to
     * turn the lamp off instead.
     */
    fun rgb(zone1: Rgb, zone2: Rgb): ByteArray = LampFrame.encode(
        CMD_RGB,
        byteArrayOf(
            b(zone1.red), b(zone1.green), b(zone1.blue),
            b(zone2.red), b(zone2.green), b(zone2.blue),
        ),
    )

    /**
     * Color for one zone while preserving the other.
     *
     * [otherColor] must be the other zone's current color, for the same reason as
     * [brightness].
     */
    fun color(zone: Zone, color: Rgb, otherColor: Rgb): ByteArray = when (zone) {
        Zone.ZONE_1 -> rgb(color, otherColor)
        Zone.ZONE_2 -> rgb(otherColor, color)
        Zone.ALL -> rgb(color, color)
    }

    fun power(mode: PowerMode): ByteArray = LampFrame.encode(CMD_POWER, byteArrayOf(b(mode.wire)))

    fun micSensitivity(value: Int): ByteArray {
        require(value in 0..MIC_SENSITIVITY_MAX) { "mic sensitivity $value" }
        return LampFrame.encode(CMD_MIC_SENSITIVITY, byteArrayOf(b(value)))
    }

    fun speed(value: Int): ByteArray {
        require(value in 0..SPEED_MAX) { "speed $value (0..$SPEED_MAX)" }
        return LampFrame.encode(CMD_SPEED, byteArrayOf(b(value)))
    }

    fun direction(reverse: Boolean): ByteArray =
        LampFrame.encode(CMD_DIRECTION, byteArrayOf(b(if (reverse) 1 else 0)))

    fun rotationFollow(on: Boolean): ByteArray =
        LampFrame.encode(CMD_ROT_FOLLOW, byteArrayOf(b(if (on) 1 else 0)))

    fun ledCount(count: Int): ByteArray {
        require(count in 0..LED_COUNT_MAX) { "led count $count (0..$LED_COUNT_MAX)" }
        return LampFrame.encode(CMD_LED_COUNT, byteArrayOf(b(count)))
    }

    /** `p1`/`p2` semantics are only partially decoded — see the protocol doc §8. */
    fun mode(p1: Int, p2: Int): ByteArray =
        LampFrame.encode(CMD_MODE, byteArrayOf(b(p1), b(p2)))

    fun builtinMode(): ByteArray =
        LampFrame.encode(CMD_SYSTEM, byteArrayOf(b(SYSTEM_BUILTIN_MODE)))

    fun rfPair(): ByteArray =
        LampFrame.encode(CMD_SYSTEM, byteArrayOf(b(SYSTEM_RF_PAIR)))

    /**
     * Flow (流水) mode bit-packing: `((step - 1) << 4) | (familyIndex + 10)`.
     *
     * @param familyIndex 0-based index into the UI's mode families
     *        (七彩 / 幻彩 / 魔幻 / 律动 / 跟随), so the low nibble becomes 0x0A..0x0E.
     * @param step 1-based step within that family.
     */
    fun flow(familyIndex: Int, step: Int): ByteArray {
        require(familyIndex in 0..4) { "familyIndex $familyIndex (0..4)" }
        require(step in 1..16) { "step $step (1..16)" }
        val packed = ((step - 1) shl 4) or (familyIndex + 10)
        return LampFrame.encode(CMD_FLOW, byteArrayOf(b(packed)))
    }

    private fun b(value: Int): Byte = (value and 0xFF).toByte()
}

/**
 * The legacy `0x7E ... 0xEF` frame family, used by the original app *only* for
 * the shake-to-change quick actions. The encoding is not understood — these are
 * opaque constants. Prefer re-implementing the feature with [LampCommand.color].
 */
object LegacyShakeFrame {
    val ALIAS_OFF: ByteArray = bytes(0x7E, 0x04, 0x04, 0x00, 0x00, 0x00, 0xFF, 0x00, 0xEF)
    val ALIAS_WHITE: ByteArray = bytes(0x7E, 0x04, 0x04, 0x00, 0x00, 0x01, 0xFF, 0x00, 0xEF)
    val ALIAS_RAINBOW_FLASH: ByteArray = bytes(0x7E, 0x05, 0x03, 0x95, 0x03, 0xFF, 0xFF, 0x00, 0xEF)
    val ALIAS_UNKNOWN: ByteArray = bytes(0x7E, 0x07, 0x05, 0x03, 0xFF, 0xFF, 0xFF, 0xFF, 0xEF)

    private fun bytes(vararg v: Int) = ByteArray(v.size) { (v[it] and 0xFF).toByte() }
}
