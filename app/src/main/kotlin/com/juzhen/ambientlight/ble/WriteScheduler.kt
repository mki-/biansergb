package com.juzhen.ambientlight.ble

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Serialises BLE writes and coalesces redundant ones.
 *
 * The original app called `writeNoRsp` straight from the UI thread with no queue
 * at all, so dragging a slider or running the audio reactivity flooded the link
 * and the firmware silently dropped frames.
 *
 * Behaviour:
 *  * strictly one write in flight ([FrameSender.write] suspends until the GATT
 *    callback fires),
 *  * pending frames are keyed by `mergeKey`; a newer frame for the same key
 *    replaces the older one, so a 60 fps drag produces a handful of writes,
 *  * a minimum gap between writes keeps the stack from erroring out.
 */
class WriteScheduler(
    private val scope: CoroutineScope,
    private val transport: FrameSender,
    private val minIntervalMs: Long = 60L,
) {

    fun interface FrameSender {
        /** Suspend until the write completes; return false on failure. */
        suspend fun write(frame: ByteArray): Boolean
    }

    private class Pending(val frame: ByteArray, val submitOrder: Long)

    private val lock = Any()
    private val pending = LinkedHashMap<String, Pending>()
    private var order = 0L
    private val wake = Channel<Unit>(Channel.CONFLATED)

    private var job: Job? = null

    /** Number of frames dropped because a newer one superseded them. */
    @Volatile
    var coalesced: Int = 0
        private set

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch { loop() }
    }

    fun stop() {
        job?.cancel()
        job = null
        synchronized(lock) { pending.clear() }
    }

    fun submit(mergeKey: String, frame: ByteArray) {
        synchronized(lock) {
            if (pending.containsKey(mergeKey)) coalesced++
            pending[mergeKey] = Pending(frame, ++order)
            // keep insertion order stable so unrelated commands are not reordered
            if (pending.size > MAX_PENDING) {
                val oldest = pending.entries.minByOrNull { it.value.submitOrder }?.key
                if (oldest != null && oldest != mergeKey) pending.remove(oldest)
            }
        }
        wake.trySend(Unit)
    }

    private suspend fun loop() {
        while (currentCoroutineContext().isActive) {
            val next = synchronized(lock) {
                // always send the most recently submitted pending frame first
                val entry = pending.entries.maxByOrNull { it.value.submitOrder }
                if (entry == null) null else {
                    pending.remove(entry.key)
                    entry.value.frame
                }
            }
            if (next == null) {
                wake.receive()
                continue
            }
            transport.write(next)
            if (minIntervalMs > 0) delay(minIntervalMs)
        }
    }

    private companion object {
        const val MAX_PENDING = 32
    }
}
