package com.juzhen.ambient.protocol

/**
 * Frame codec for the ambient-lamp BLE protocol.
 *
 * ```
 * +------+------+------+---------------------+------+------+
 * | 0x55 |  L   | CMD  | PAYLOAD (L-1 bytes) |  CK  | 0xAA |
 * +------+------+------+---------------------+------+------+
 *   L  = 1 + payload.size          (CMD counts as one)
 *   CK = (~(L + CMD + sum(payload))) & 0xFF
 * ```
 *
 * Verified against three frames the original app hard-codes
 * (see `LampFrameTest`): `55 01 00 FE AA`, `55 02 16 01 E6 AA`, `55 02 16 03 E4 AA`.
 *
 * This module is deliberately free of any `android.*` import so it can be unit
 * tested on the JVM and later reused from Kotlin Multiplatform.
 */
object LampFrame {

    const val SOF: Int = 0x55
    const val EOF: Int = 0xAA

    /** SOF + L + CK + EOF */
    const val OVERHEAD: Int = 4

    /** L is 1..0xFE, so the largest legal frame is 1 + 254 + 3 = 258 bytes. */
    const val MAX_FRAME_SIZE: Int = 0xFE + OVERHEAD

    private val EMPTY = ByteArray(0)

    /** `body` is `L | CMD | payload...` — everything between SOF and CK. */
    fun checksum(body: ByteArray): Int {
        var sum = 0
        for (b in body) sum += b.toInt() and 0xFF
        return sum.inv() and 0xFF
    }

    fun encode(cmd: Int, payload: ByteArray = EMPTY): ByteArray {
        require(cmd in 0..0xFF) { "cmd out of range: $cmd" }
        require(payload.size <= 0xFD) { "payload too long: ${payload.size}" }

        val body = ByteArray(payload.size + 2)
        body[0] = (1 + payload.size).toByte()
        body[1] = cmd.toByte()
        payload.copyInto(body, destinationOffset = 2)

        val out = ByteArray(body.size + 3)   // SOF + body + CK + EOF'ish
        // layout: [0]=SOF  [1..body.size]=body  [body.size+1]=CK  [body.size+2]=EOF
        out[0] = SOF.toByte()
        body.copyInto(out, destinationOffset = 1)
        out[body.size + 1] = checksum(body).toByte()
        out[body.size + 2] = EOF.toByte()
        return out
    }

    @Throws(BadFrameException::class)
    fun decode(raw: ByteArray): Frame {
        if (raw.size < 5) {
            throw BadFrameException("frame too short: ${raw.size} bytes")
        }
        if (raw[0] != SOF.toByte()) {
            throw BadFrameException("bad SOF 0x%02X".format(raw[0].toInt() and 0xFF))
        }
        if (raw[raw.size - 1] != EOF.toByte()) {
            throw BadFrameException("bad EOF 0x%02X".format(raw[raw.size - 1].toInt() and 0xFF))
        }
        val length = raw[1].toInt() and 0xFF
        val expected = length + OVERHEAD
        if (raw.size != expected) {
            throw BadFrameException("length byte $length implies $expected bytes, got ${raw.size}")
        }
        val body = raw.copyOfRange(1, raw.size - 2)
        val actual = raw[raw.size - 2].toInt() and 0xFF
        val expectedCk = checksum(body)
        if (actual != expectedCk) {
            throw BadFrameException("bad checksum: got 0x%02X want 0x%02X".format(actual, expectedCk))
        }
        return Frame(
            cmd = raw[2].toInt() and 0xFF,
            payload = raw.copyOfRange(3, raw.size - 2),
        )
    }
}

class BadFrameException(message: String) : IllegalArgumentException(message)

class Frame(val cmd: Int, val payload: ByteArray) {

    fun toBytes(): ByteArray = LampFrame.encode(cmd, payload)

    fun hex(): String = toBytes().joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Frame) return false
        return cmd == other.cmd && payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int = 31 * cmd + payload.contentHashCode()

    override fun toString(): String = "Frame(cmd=0x%02X, payload=%s)".format(
        cmd,
        payload.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) },
    )
}

/**
 * Reassembles frames out of BLE notification chunks.
 *
 * BLE delivers 20-byte fragments (default MTU) so a frame can be split, and a
 * noisy link can inject garbage — including stray 0x55 bytes that look like a
 * start-of-frame. Naively trusting the first 0x55 makes the parser wait forever
 * for a bogus 85-byte frame, so instead this decoder scans for the earliest
 * offset at which a *complete, checksum-valid* frame is fully buffered.
 */
class FrameStreamDecoder {

    private val buffer = ArrayList<Byte>(LampFrame.MAX_FRAME_SIZE * 2)

    fun feed(chunk: ByteArray): List<Frame> {
        for (b in chunk) buffer.add(b)

        val frames = mutableListOf<Frame>()
        while (true) {
            var foundAt = -1
            var foundLength = 0
            var foundFrame: Frame? = null

            var i = 0
            while (i + 1 < buffer.size) {
                if (buffer[i] == LampFrame.SOF.toByte()) {
                    val need = (buffer[i + 1].toInt() and 0xFF) + LampFrame.OVERHEAD
                    if (i + need <= buffer.size) {
                        val candidate = ByteArray(need) { buffer[i + it] }
                        val parsed = runCatching { LampFrame.decode(candidate) }.getOrNull()
                        if (parsed != null) {
                            foundAt = i
                            foundLength = need
                            foundFrame = parsed
                            break
                        }
                    }
                }
                i++
            }

            if (foundFrame == null) break
            frames += foundFrame
            buffer.subList(0, foundAt + foundLength).clear()
        }

        // Drop leading noise but keep the tail that may be an incomplete frame:
        // start from the last offset whose length byte is plausibly a frame.
        var keepFrom = buffer.size
        for (i in buffer.indices) {
            if (buffer[i] != LampFrame.SOF.toByte()) continue
            val plausible = i + 1 < buffer.size &&
                (buffer[i + 1].toInt() and 0xFF) in 1..0xFE
            if (plausible || i + 1 == buffer.size) {
                keepFrom = i
                break
            }
        }
        if (keepFrom > 0) buffer.subList(0, keepFrom).clear()

        // last-resort guard so a permanently desynchronised link cannot grow forever
        if (buffer.size > LampFrame.MAX_FRAME_SIZE * 2) buffer.clear()
        return frames
    }

    fun reset() = buffer.clear()
}
