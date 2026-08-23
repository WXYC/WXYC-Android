package playback

import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SeamlessStreamReaderTest {

    /**
     * Stands in for the Icecast mount: one continuous broadcast, served over
     * connections that can drop. A reopened connection starts [burstBytes]
     * behind the live edge, the way burst-on-connect actually behaves.
     */
    private class FakeMount(
        val broadcast: ByteArray,
        val dropAtPositions: MutableList<Int> = mutableListOf(),
        val burstBytes: Int,
        val failedReopens: Int = 0,
        val outageBytes: Int = 0
    ) : LiveByteStream {
        var position = 0
        private var reopenFailures = 0
        private var closed = false
        var openCount = 0
            private set

        override fun open() {
            if (openCount > 0) {
                if (reopenFailures++ < failedReopens) throw IOException("connect refused")
                // Reopened: the live edge has moved on by the outage, and the
                // server replays its burst of recent history from there.
                val liveEdge = position + outageBytes
                position = maxOf(0, liveEdge - burstBytes)
            }
            openCount++
            closed = false
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (closed || position >= broadcast.size) return -1
            val nextDrop = dropAtPositions.firstOrNull()
            if (nextDrop != null && position >= nextDrop) {
                dropAtPositions.removeAt(0)
                return -1
            }
            var n = minOf(length, broadcast.size - position, 4_096)
            if (nextDrop != null) n = minOf(n, nextDrop - position)
            broadcast.copyInto(buffer, offset, position, position + n)
            position += n
            return n
        }

        override fun close() {
            closed = true
        }
    }

    private fun drain(reader: SeamlessStreamReader, limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(1_024)
        while (out.size() < limit) {
            val n = reader.read(buf, 0, minOf(buf.size, limit - out.size()))
            if (n == -1) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun reader(mount: FakeMount): SeamlessStreamReader {
        var clock = 0L
        return SeamlessStreamReader(
            stream = mount,
            reconnectDelaysMs = longArrayOf(0, 0, 0),
            sleep = {},
            nowMs = { clock += 50; clock }
        )
    }

    @Test
    fun `passes audio through untouched while the connection holds`() {
        val broadcast = Random(1).nextBytes(50_000)
        val mount = FakeMount(broadcast, burstBytes = 53_000)
        val reader = reader(mount).also { it.open() }

        assertArrayEquals(broadcast, drain(reader, broadcast.size))
        assertEquals(1, mount.openCount)
    }

    @Test
    fun `a dropped connection leaves no gap and no repeat in the audio`() {
        // The invariant that matters: what the player receives across a drop is
        // byte-identical to the uninterrupted broadcast.
        val broadcast = Random(2).nextBytes(200_000)
        val mount = FakeMount(
            broadcast,
            dropAtPositions = mutableListOf(80_000),
            burstBytes = 53_000
        )
        val reader = reader(mount).also { it.open() }

        val heard = drain(reader, 150_000)

        assertArrayEquals(broadcast.copyOfRange(0, 150_000), heard)
        assertEquals(2, mount.openCount)
        assertEquals(1, reader.reconnectCount)
        assertTrue(reader.lastSpliceWasSeamless)
    }

    @Test
    fun `survives several drops in one listening session`() {
        val broadcast = Random(3).nextBytes(400_000)
        val mount = FakeMount(
            broadcast,
            dropAtPositions = mutableListOf(80_000, 200_000, 320_000),
            burstBytes = 53_000
        )
        val reader = reader(mount).also { it.open() }

        assertArrayEquals(broadcast.copyOfRange(0, 350_000), drain(reader, 350_000))
        assertEquals(3, reader.reconnectCount)
    }

    @Test
    fun `an outage longer than the burst leaves a hole rather than replaying audio`() {
        // The audio during the outage never arrived; no client can invent it. What
        // must not happen is the seam also repeating or dropping extra audio.
        val broadcast = Random(4).nextBytes(600_000)
        val skipAhead = 120_000
        val mount = FakeMount(
            broadcast,
            dropAtPositions = mutableListOf(80_000),
            burstBytes = 53_000,
            outageBytes = skipAhead
        )
        val reader = reader(mount).also { it.open() }

        val heard = drain(reader, 150_000)

        val resumePoint = 80_000 + skipAhead - 53_000
        assertArrayEquals(broadcast.copyOfRange(0, 80_000), heard.copyOfRange(0, 80_000))
        assertArrayEquals(
            broadcast.copyOfRange(resumePoint, resumePoint + 20_000),
            heard.copyOfRange(80_000, 100_000)
        )
        assertTrue("splice should not claim to be seamless", !reader.lastSpliceWasSeamless)
    }

    @Test
    fun `retries a refused connection and still splices seamlessly`() {
        val broadcast = Random(5).nextBytes(200_000)
        val mount = FakeMount(
            broadcast,
            dropAtPositions = mutableListOf(80_000),
            burstBytes = 53_000,
            failedReopens = 2
        )
        val reader = reader(mount).also { it.open() }

        assertArrayEquals(broadcast.copyOfRange(0, 150_000), drain(reader, 150_000))
        assertTrue(reader.lastSpliceWasSeamless)
    }

    @Test
    fun `gives up and reports end of stream once retries are exhausted`() {
        val broadcast = Random(6).nextBytes(200_000)
        val mount = FakeMount(
            broadcast,
            dropAtPositions = mutableListOf(80_000),
            burstBytes = 53_000,
            failedReopens = 99
        )
        val reader = reader(mount).also { it.open() }

        val heard = drain(reader, 150_000)

        assertArrayEquals(broadcast.copyOfRange(0, 80_000), heard)
        assertEquals(-1, reader.read(ByteArray(16), 0, 16))
    }

    @Test
    fun `a zero-length read is not mistaken for end of stream`() {
        // DataReader's contract. Returning -1 here would tell ExoPlayer the
        // stream ended and undo the whole point of this class.
        val mount = FakeMount(Random(8).nextBytes(50_000), burstBytes = 53_000)
        val reader = reader(mount).also { it.open() }

        assertEquals(0, reader.read(ByteArray(16), 0, 0))
        assertEquals(1, mount.openCount)
    }

    @Test
    fun `does not reconnect after close`() {
        val broadcast = Random(7).nextBytes(200_000)
        val mount = FakeMount(broadcast, mutableListOf(80_000), burstBytes = 53_000)
        val reader = reader(mount).also { it.open() }

        drain(reader, 40_000)
        reader.close()

        assertEquals(-1, reader.read(ByteArray(16), 0, 16))
        assertEquals(1, mount.openCount)
    }
}
