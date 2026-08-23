package playback

import java.io.IOException

/**
 * A reopenable byte stream. Deliberately free of media3 and Android types so the
 * recovery logic can be exercised on the JVM; [ReconnectingDataSource] adapts an
 * HTTP `DataSource` onto it.
 */
internal interface LiveByteStream {
    /** Opens a fresh connection at the live edge. Throws [IOException] on failure. */
    fun open()

    /** Reads audio, returning the byte count, or -1 once the connection ends. */
    fun read(buffer: ByteArray, offset: Int, length: Int): Int

    fun close()
}

/**
 * Reads a live stream and hides connection drops from whatever is downstream.
 *
 * When the connection ends, this reopens it and trims the audio the server
 * replays, so the bytes handed downstream stay continuous: no silence, and no
 * repeated seconds. The player never sees the stream end, so it never leaves
 * `STATE_READY` — which also keeps the media notification and the foreground
 * service in place.
 *
 * This works because the player is always running behind the live edge by about
 * the server's burst size, and a reopened connection starts behind the live edge
 * by that same amount. The two line up, so the replayed prefix is exactly the
 * audio still queued for playback, and dropping it dovetails the connections.
 *
 * The one case that cannot be papered over is an outage lasting longer than the
 * burst: that audio never reached the device, and no amount of splicing invents
 * it. The seam is then a genuine hole, reported through [lastSpliceWasSeamless],
 * and nothing extra is discarded around it.
 *
 * Recovery runs on the caller's read thread. That is intentional — the player's
 * loader blocks in [read] while its own buffer keeps playing, which is the
 * headroom the reconnect runs inside.
 */
internal class SeamlessStreamReader(
    private val stream: LiveByteStream,
    private val reconnectDelaysMs: LongArray = DEFAULT_RECONNECT_DELAYS_MS,
    private val maxProbeBytes: Int = DEFAULT_MAX_PROBE_BYTES,
    private val probeTimeoutMs: Long = DEFAULT_PROBE_TIMEOUT_MS,
    private val sleep: (Long) -> Unit = { if (it > 0) Thread.sleep(it) },
    private val nowMs: () -> Long = System::currentTimeMillis
) {

    companion object {
        /**
         * The first retry is immediate: the common case is a socket the server
         * closed while the network is perfectly healthy, and the player's buffer
         * is draining in real time while we retry.
         */
        val DEFAULT_RECONNECT_DELAYS_MS = longArrayOf(0, 250, 500, 1_000, 2_000)

        /** Comfortably more than the ~3.3s burst the mount serves at 128 kbps. */
        const val DEFAULT_MAX_PROBE_BYTES = 192 * 1024

        /**
         * Once the burst is exhausted the socket only delivers in real time, so
         * probing past this point cannot find an anchor that isn't there.
         */
        const val DEFAULT_PROBE_TIMEOUT_MS = 2_000L

        private const val PROBE_CHUNK = 16 * 1024
    }

    private val tail = ByteArray(StreamSplicer.ANCHOR_BYTES)
    private var tailLength = 0

    private var pending: ByteArray? = null
    private var pendingOffset = 0
    private var pendingLength = 0

    private var closed = false
    private var ended = false

    /** Reconnects performed during this open. */
    var reconnectCount: Int = 0
        private set

    /** Whether the most recent reconnect produced an unbroken splice. */
    var lastSpliceWasSeamless: Boolean = true
        private set

    fun open() {
        stream.open()
        closed = false
        ended = false
    }

    fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        // DataReader's contract: a zero-length read is not end-of-input.
        if (length == 0) return 0
        if (closed || ended) return -1

        servePending(buffer, offset, length)?.let { return it }

        val n = try {
            stream.read(buffer, offset, length)
        } catch (e: IOException) {
            -1
        }
        if (n != -1) {
            recordTail(buffer, offset, n)
            return n
        }

        if (closed || !reconnect()) {
            ended = true
            return -1
        }

        servePending(buffer, offset, length)?.let { return it }
        return read(buffer, offset, length)
    }

    fun close() {
        closed = true
        pending = null
        stream.close()
    }

    private fun servePending(buffer: ByteArray, offset: Int, length: Int): Int? {
        val queued = pending ?: return null
        val n = minOf(length, pendingLength - pendingOffset)
        queued.copyInto(buffer, offset, pendingOffset, pendingOffset + n)
        pendingOffset += n
        if (pendingOffset >= pendingLength) pending = null
        recordTail(buffer, offset, n)
        return n
    }

    /**
     * Reopens the stream and works out where its replayed history stops.
     * Returns false only once every retry has been spent.
     */
    private fun reconnect(): Boolean {
        stream.close()

        for (delay in reconnectDelaysMs) {
            if (closed) return false
            sleep(delay)
            try {
                stream.open()
            } catch (e: IOException) {
                continue
            }

            reconnectCount++
            val probe = ByteArray(maxProbeBytes)
            var probeLength = 0
            var droppedWhileProbing = false
            val deadline = nowMs() + probeTimeoutMs

            while (probeLength < maxProbeBytes && nowMs() < deadline) {
                val want = minOf(PROBE_CHUNK, maxProbeBytes - probeLength)
                val n = try {
                    stream.read(probe, probeLength, want)
                } catch (e: IOException) {
                    -1
                }
                if (n == -1) {
                    droppedWhileProbing = true
                    break
                }
                probeLength += n

                val resume = StreamSplicer.findResumePoint(
                    tail = tail.copyOf(tailLength),
                    probe = probe,
                    probeLength = probeLength
                )
                if (resume != StreamSplicer.NO_OVERLAP) {
                    queuePending(probe, resume, probeLength)
                    lastSpliceWasSeamless = true
                    return true
                }
            }

            // The replacement died before it replayed enough to find the anchor.
            // Serving it would splice blind, repeating or dropping an unknown
            // amount, so throw it away and open another one instead.
            if (droppedWhileProbing || probeLength == 0) {
                stream.close()
                continue
            }

            // The burst ran out and the anchor was not in it: the outage outlasted
            // the server's history, so every probed byte is audio we never
            // received. Keep all of it — the hole is already as small as it can be.
            queuePending(probe, 0, probeLength)
            lastSpliceWasSeamless = false
            return true
        }
        return false
    }

    private fun queuePending(probe: ByteArray, from: Int, to: Int) {
        if (to - from <= 0) {
            pending = null
            return
        }
        pending = probe
        pendingOffset = from
        pendingLength = to
    }

    /** Keeps the trailing window used to recognise replayed audio. */
    private fun recordTail(source: ByteArray, offset: Int, count: Int) {
        if (count <= 0) return
        if (count >= tail.size) {
            source.copyInto(tail, 0, offset + count - tail.size, offset + count)
            tailLength = tail.size
            return
        }
        val keep = minOf(tailLength, tail.size - count)
        if (keep > 0) tail.copyInto(tail, 0, tailLength - keep, tailLength)
        source.copyInto(tail, keep, offset, offset + count)
        tailLength = keep + count
    }
}
