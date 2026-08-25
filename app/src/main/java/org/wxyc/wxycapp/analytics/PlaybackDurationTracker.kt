package org.wxyc.wxycapp.analytics

/**
 * Tracks how long the stream has been playing, for the `duration` property on the
 * `pause` event.
 *
 * Two things this exists to get right:
 *
 * 1. **Seconds, not milliseconds.** The iOS app owns the `duration` taxonomy and
 *    reports seconds. Both apps report into the same PostHog project, so a
 *    millisecond value here inflates every shared duration metric a thousandfold
 *    without looking obviously wrong on any single event.
 * 2. **No duration at all when the start was never observed.** Playback can begin
 *    from the media notification or a headset button, which the in-app control never
 *    sees. Measuring from an unset start reports the wall clock itself as a listen.
 *
 * The clock is injected so the arithmetic is testable off-device. Production passes
 * `SystemClock.elapsedRealtime`, which — unlike `System.currentTimeMillis` — does not
 * jump when the device clock is corrected or the time zone changes.
 */
class PlaybackDurationTracker(private val elapsedRealtimeMs: () -> Long) {

    private var startedAtMs: Long? = null

    /** Records the moment playback actually began. */
    fun onPlaybackStarted() {
        startedAtMs = elapsedRealtimeMs()
    }

    /** Forgets the start, so a later pause reports no duration instead of a stale one. */
    fun onPlaybackStopped() {
        startedAtMs = null
    }

    /**
     * Seconds of playback since the last observed start, or `null` when playback began
     * somewhere this tracker did not see. Callers should omit the `duration` property
     * entirely on `null` — a stand-in zero would be averaged in as a real listen.
     */
    fun durationSeconds(): Double? =
        startedAtMs?.let { (elapsedRealtimeMs() - it) / 1_000.0 }
}
