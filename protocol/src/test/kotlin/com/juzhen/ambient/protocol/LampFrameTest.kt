package com.juzhen.ambient.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Golden tests for the reverse-engineered protocol.
 *
 * Section A pins the frames the *original APK itself hard-codes*, which is what
 * proves the frame layout and checksum rule. Sections B–D lock in everything
 * derived from the disassembly.
 */
class LampFrameTest {

    private fun hex(bytes: ByteArray) =
        bytes.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

    private fun bytes(hex: String) =
        hex.split(" ").filter { it.isNotBlank() }
            .map { it.toInt(16).toByte() }.toByteArray()

    // ------------------------------------------------------------------ A
    @Test
    fun `frames rebuilt from APK constants`() {
        // The APK builds these three frames from fill-array-data constants plus a
        // literal checksum byte, so they are ground truth.
        assertEquals("55 01 00 FE AA", hex(LampCommand.info()))
        assertEquals("55 02 16 01 E6 AA", hex(LampCommand.builtinMode()))
        assertEquals("55 02 16 03 E4 AA", hex(LampCommand.rfPair()))
    }

    // ------------------------------------------------------------------ B
    @Test
    fun `requests match documented frames`() {
        assertEquals("55 02 10 00 ED AA", hex(LampCommand.status(LampCommand.CMD_STATUS_A)))
        assertEquals("55 02 11 00 EC AA", hex(LampCommand.status()))
        assertEquals("55 02 12 00 EB AA", hex(LampCommand.status(LampCommand.CMD_STATUS_C)))
        assertEquals("55 02 05 02 F6 AA", hex(LampCommand.power(PowerMode.ON)))
        assertEquals("55 02 05 00 F8 AA", hex(LampCommand.power(PowerMode.OFF)))
        assertEquals("55 02 05 01 F7 AA", hex(LampCommand.power(PowerMode.FOLLOW)))
        assertEquals("55 03 02 64 64 32 AA", hex(LampCommand.brightness(100, 100)))
        assertEquals(
            "55 07 04 FF 00 00 00 00 00 F5 AA",
            hex(LampCommand.rgb(Rgb(255, 0, 0), Rgb.BLACK)),
        )
        assertEquals(
            "55 07 04 FF FF FF FF FF FF FA AA",
            hex(LampCommand.rgb(Rgb.WHITE, Rgb.WHITE)),
        )
        assertEquals("55 02 23 03 D7 AA", hex(LampCommand.speed(3)))
        assertEquals("55 02 24 00 D9 AA", hex(LampCommand.direction(false)))
        assertEquals("55 02 24 01 D8 AA", hex(LampCommand.direction(true)))
        assertEquals("55 02 25 01 D7 AA", hex(LampCommand.rotationFollow(true)))
        assertEquals("55 02 26 96 41 AA", hex(LampCommand.ledCount(150)))
        assertEquals("55 02 06 32 C5 AA", hex(LampCommand.micSensitivity(50)))
    }

    @Test
    fun `single zone command preserves the other zone`() {
        // brightness for zone 1, zone 2 stays at 40 -> payload 100,40
        val frame = LampCommand.brightness(Zone.ZONE_1, 100, 40)
        assertEquals(LampCommand.CMD_BRIGHTNESS, LampFrame.decode(frame).cmd)
        assertArrayEquals(byteArrayOf(100, 40), LampFrame.decode(frame).payload)

        val color = LampCommand.color(Zone.ZONE_2, Rgb(0, 0, 255), Rgb(255, 0, 0))
        assertArrayEquals(
            byteArrayOf(0xFF.toByte(), 0, 0, 0, 0, 0xFF.toByte()),
            LampFrame.decode(color).payload,
        )
    }

    @Test
    fun `flow mode bit packing`() {
        // family 0 (七彩), step 3 -> ((3-1)<<4)|10 = 0x2A
        assertEquals(
            "55 02 22 2A B1 AA",
            hex(LampCommand.flow(familyIndex = 0, step = 3)),
        )
        // family 4 (跟随), step 1 -> (0<<4)|14 = 0x0E
        assertEquals(
            "55 02 22 0E CD AA",
            hex(LampCommand.flow(familyIndex = 4, step = 1)),
        )
    }

    @Test
    fun `range validation rejects bad values`() {
        listOf<() -> ByteArray>(
            { LampCommand.brightness(101, 0) },
            { LampCommand.brightness(0, -1) },
            { LampCommand.speed(6) },
            { LampCommand.ledCount(151) },
            { LampCommand.micSensitivity(101) },
            { LampCommand.flow(5, 1) },
            { LampCommand.flow(0, 0) },
            { Rgb(256, 0, 0) },
        ).forEach { call ->
            try {
                call()
                fail("expected IllegalArgumentException")
            } catch (expected: IllegalArgumentException) {
                // ok
            }
        }
    }

    // ------------------------------------------------------------------ C
    @Test
    fun `state frame parses`() {
        // built with the same encoder, mirroring what firmware sends
        val payload = bytes(
            "50 03 FF 00 00 28 05 00 00 FF 01 20 00",
        )
        val frame = LampFrame.decode(LampFrame.encode(LampState.CMD_STATE, payload))
        assertEquals(14, frame.payload.size + 1)   // L = 1 + payload

        val state = LampState.from(frame)!!
        assertEquals(80, state.brightness1)
        assertEquals(3, state.mode1)
        assertEquals(Rgb(255, 0, 0), state.zone1)
        assertEquals(40, state.brightness2)
        assertEquals(5, state.mode2)
        assertEquals(Rgb(0, 0, 255), state.zone2)
        assertTrue(state.power)
        assertEquals(32, state.micLevel)
    }

    @Test
    fun `non state frames are ignored by the state parser`() {
        assertNull(LampState.from(LampFrame.decode(LampCommand.info())))
    }

    // ------------------------------------------------------------------ D
    @Test
    fun `stream decoder reassembles fragmented frames`() {
        val frame = LampFrame.encode(
            LampState.CMD_STATE,
            bytes("50 03 FF 00 00 28 05 00 00 FF 01 20 00"),
        )
        assertEquals(18, frame.size)

        val decoder = FrameStreamDecoder()
        // feed in 5-byte chunks to simulate small MTU fragmentation
        val out = mutableListOf<Frame>()
        frame.toList().chunked(5).forEach { chunk ->
            out += decoder.feed(chunk.toByteArray())
        }
        assertEquals(1, out.size)
        assertEquals(80, LampState.from(out[0])!!.brightness1)
    }

    @Test
    fun `stream decoder resyncs past noise`() {
        val good = LampCommand.speed(3)
        val decoder = FrameStreamDecoder()
        val out = decoder.feed(byteArrayOf(0x00, 0x13, 0x55) + good + byteArrayOf(0xFF.toByte()))
        assertEquals(1, out.size)
        assertArrayEquals(good, out[0].toBytes())
    }

    @Test
    fun `corrupted checksum is rejected`() {
        val bad = LampCommand.speed(3)
        bad[bad.size - 2] = (bad[bad.size - 2].toInt() xor 0xFF).toByte()
        try {
            LampFrame.decode(bad)
            fail("expected BadFrameException")
        } catch (expected: BadFrameException) {
            // ok
        }
    }

    @Test
    fun `bad start and end bytes are rejected`() {
        listOf(
            bytes("54 02 05 02 F6 AA"),   // wrong SOF
            bytes("55 02 05 02 F6 AB"),   // wrong EOF
            bytes("55 02 05 02 F6"),      // truncated
        ).forEach { raw ->
            try {
                LampFrame.decode(raw)
                fail("expected BadFrameException for ${hex(raw)}")
            } catch (expected: BadFrameException) {
                // ok
            }
        }
    }

    @Test
    fun `checksum known plaintext`() {
        // these three are the values baked into the APK
        assertEquals(0xFE, LampFrame.checksum(bytes("01 00")))
        assertEquals(0xE6, LampFrame.checksum(bytes("02 16 01")))
        assertEquals(0xE4, LampFrame.checksum(bytes("02 16 03")))
    }

    @Test
    fun `ascii report trims trailing byte`() {
        // firmware sends "V1.2.3" followed by a terminator byte
        val payload = "V1.2.3".toByteArray(Charsets.UTF_8) + byteArrayOf(0x00)
        val frame = LampFrame.decode(LampFrame.encode(0x08, payload))
        assertEquals("V1.2.3", AsciiReport.decode(frame))
    }
}
