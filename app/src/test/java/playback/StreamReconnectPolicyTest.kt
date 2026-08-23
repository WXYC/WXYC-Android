package playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StreamReconnectPolicyTest {

    private lateinit var policy: StreamReconnectPolicy

    @Before
    fun setUp() {
        policy = StreamReconnectPolicy(initialDelayMs = 1_000, maxDelayMs = 30_000)
    }

    @Test
    fun `first attempt waits the initial delay`() {
        assertEquals(1_000L, policy.nextDelayMs())
        assertEquals(1, policy.attemptCount)
    }

    @Test
    fun `consecutive attempts back off exponentially`() {
        assertEquals(1_000L, policy.nextDelayMs())
        assertEquals(2_000L, policy.nextDelayMs())
        assertEquals(4_000L, policy.nextDelayMs())
        assertEquals(8_000L, policy.nextDelayMs())
    }

    @Test
    fun `backoff is capped so a station outage never stalls recovery`() {
        repeat(20) { policy.nextDelayMs() }

        assertEquals(30_000L, policy.nextDelayMs())
    }

    @Test
    fun `a healthy stream resets the backoff`() {
        policy.nextDelayMs()
        policy.nextDelayMs()

        policy.onStreamHealthy()

        assertEquals(0, policy.attemptCount)
        assertEquals(1_000L, policy.nextDelayMs())
    }

    @Test
    fun `a sustained stretch of playback resets the backoff`() {
        policy.nextDelayMs()
        policy.nextDelayMs()

        policy.onPlaybackInterrupted(playedForMs = 60_000)

        assertEquals(0, policy.attemptCount)
    }

    @Test
    fun `a stream that dies again immediately keeps backing off`() {
        policy.nextDelayMs()
        policy.nextDelayMs()

        // Connected, played for a moment, dropped again: a flapping mount must
        // not reset the backoff or the client spins in a reconnect loop.
        policy.onPlaybackInterrupted(playedForMs = 500)

        assertEquals(2, policy.attemptCount)
        assertEquals(4_000L, policy.nextDelayMs())
    }

    @Test
    fun `reconnects only while the listener still wants audio`() {
        assertTrue(policy.shouldReconnect(userWantsPlayback = true))
        assertFalse(policy.shouldReconnect(userWantsPlayback = false))
    }

    @Test
    fun `a user-initiated pause resets the backoff so the next play is immediate`() {
        policy.nextDelayMs()
        policy.nextDelayMs()

        assertFalse(policy.shouldReconnect(userWantsPlayback = false))

        assertEquals(0, policy.attemptCount)
    }

    @Test
    fun `retries indefinitely because a radio stream is expected to come back`() {
        repeat(500) { policy.nextDelayMs() }

        assertTrue(policy.shouldReconnect(userWantsPlayback = true))
    }
}
