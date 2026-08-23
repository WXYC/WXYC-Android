package playback

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class StreamSplicerTest {

    private fun audio(seed: Int, size: Int): ByteArray = Random(seed).nextBytes(size)

    @Test
    fun `finds where the reopened stream stops repeating what we already delivered`() {
        // The server bursts recent history on connect, so a fresh connection
        // starts behind the live edge and re-sends audio we already have.
        val stream = audio(seed = 1, size = 40_000)
        val delivered = stream.copyOfRange(0, 30_000)
        val burst = stream.copyOfRange(20_000, 40_000) // 10k of overlap, then 10k new

        val resume = StreamSplicer.findResumePoint(
            tail = delivered.takeLast(8_000).toByteArray(),
            probe = burst,
            probeLength = burst.size
        )

        assertEquals(10_000, resume)
        // Everything from the resume point on must be audio we have not sent yet.
        assertEquals(
            stream.copyOfRange(30_000, 40_000).toList(),
            burst.copyOfRange(resume, burst.size).toList()
        )
    }

    @Test
    fun `resumes at the end of the probe when the burst is entirely stale`() {
        val stream = audio(seed = 2, size = 30_000)
        val delivered = stream.copyOfRange(0, 30_000)
        val burst = stream.copyOfRange(10_000, 30_000) // all of it already delivered

        val resume = StreamSplicer.findResumePoint(
            tail = delivered.takeLast(8_000).toByteArray(),
            probe = burst,
            probeLength = burst.size
        )

        assertEquals(burst.size, resume)
    }

    @Test
    fun `reports no overlap when the outage outlasted the server's burst`() {
        // A long outage means the new burst is entirely audio we never received.
        // There is a real hole in the content, so nothing may be discarded.
        val delivered = audio(seed = 3, size = 20_000)
        val burst = audio(seed = 4, size = 20_000)

        val resume = StreamSplicer.findResumePoint(
            tail = delivered.takeLast(8_000).toByteArray(),
            probe = burst,
            probeLength = burst.size
        )

        assertEquals(StreamSplicer.NO_OVERLAP, resume)
    }

    @Test
    fun `prefers the most recent match when the anchor repeats in the burst`() {
        // Silence or a repeated loop can make an anchor appear more than once.
        // Resuming at the earliest match would replay audio, so take the latest.
        val anchor = ByteArray(StreamSplicer.ANCHOR_BYTES) { 0 }
        val probe = anchor + ByteArray(500) { 0 } + anchor + audio(seed = 5, size = 1_000)

        val resume = StreamSplicer.findResumePoint(
            tail = anchor,
            probe = probe,
            probeLength = probe.size
        )

        assertEquals(probe.size - 1_000, resume)
    }

    @Test
    fun `reports no overlap when the probe is shorter than the anchor`() {
        val delivered = audio(seed = 6, size = 20_000)
        val probe = delivered.takeLast(10).toByteArray()

        val resume = StreamSplicer.findResumePoint(
            tail = delivered,
            probe = probe,
            probeLength = probe.size
        )

        assertEquals(StreamSplicer.NO_OVERLAP, resume)
    }

    @Test
    fun `honours probeLength and ignores stale bytes past it`() {
        val stream = audio(seed = 7, size = 40_000)
        val delivered = stream.copyOfRange(0, 30_000)
        val burst = stream.copyOfRange(20_000, 35_000)
        // A buffer larger than the bytes actually read, with junk past the mark.
        val probe = burst + audio(seed = 8, size = 5_000)

        val resume = StreamSplicer.findResumePoint(
            tail = delivered.takeLast(8_000).toByteArray(),
            probe = probe,
            probeLength = burst.size
        )

        assertEquals(10_000, resume)
    }
}
