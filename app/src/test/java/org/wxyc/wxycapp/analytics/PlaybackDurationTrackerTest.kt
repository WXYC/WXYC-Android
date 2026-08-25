package org.wxyc.wxycapp.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PlaybackDurationTrackerTest {

    private var clockMs = 0L
    private lateinit var tracker: PlaybackDurationTracker

    @Before
    fun setUp() {
        clockMs = 5_000L
        tracker = PlaybackDurationTracker { clockMs }
    }

    @Test
    fun `duration is reported in seconds, not milliseconds`() {
        tracker.onPlaybackStarted()
        clockMs += 90_000L

        assertEquals(90.0, tracker.durationSeconds()!!, 0.001)
    }

    @Test
    fun `short listens keep sub-second precision`() {
        tracker.onPlaybackStarted()
        clockMs += 1_762L

        assertEquals(1.762, tracker.durationSeconds()!!, 0.0001)
    }

    @Test
    fun `an unobserved start reports no duration rather than the wall clock`() {
        // Regression guard: playback can begin from the media notification or a
        // headset button, which the in-app control never sees. The old code
        // subtracted a start time that defaulted to zero, so those pauses reported
        // the entire Unix epoch (~1.787e12) as a single listen.
        val epochScaleClock = PlaybackDurationTracker { 1_787_542_600_297L }

        assertNull(epochScaleClock.durationSeconds())
    }

    @Test
    fun `a stop clears the start so a later pause cannot reuse it`() {
        tracker.onPlaybackStarted()
        clockMs += 10_000L
        tracker.onPlaybackStopped()
        clockMs += 10_000L

        assertNull(tracker.durationSeconds())
    }

    @Test
    fun `duration measures from the most recent start`() {
        tracker.onPlaybackStarted()
        clockMs += 10_000L
        tracker.onPlaybackStopped()
        tracker.onPlaybackStarted()
        clockMs += 3_000L

        assertEquals(3.0, tracker.durationSeconds()!!, 0.001)
    }

    @Test
    fun `reading the duration does not consume the start`() {
        tracker.onPlaybackStarted()
        clockMs += 4_000L

        assertEquals(4.0, tracker.durationSeconds()!!, 0.001)
        clockMs += 1_000L
        assertEquals(5.0, tracker.durationSeconds()!!, 0.001)
    }
}
