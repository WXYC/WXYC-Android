package playback

/**
 * Decides when a dropped live-stream connection should be re-established.
 *
 * The WXYC stream is an unbounded Icecast MP3 mount served over HTTP/1.0 with
 * `Connection: Close` and no content length. ExoPlayer models that as a
 * *progressive* source, so when the server closes the socket the loader reports
 * a clean end of input rather than an error: `ProgressiveMediaPeriod` sets
 * `loadingFinished` and the player settles in `Player.STATE_ENDED` with no
 * `PlaybackException` ever raised. Nothing in ExoPlayer retries that, because as
 * far as it knows the file simply ended. Left alone the player stays ended and
 * audio never comes back until the listener notices and taps play.
 *
 * This policy drives the reconnect instead, backing off exponentially so that a
 * station-side outage doesn't turn every listener into a request flood, and
 * capping the delay so recovery still feels prompt once the stream returns.
 *
 * The policy is deliberately free of Android and ExoPlayer types so the decision
 * logic can be unit tested on its own; [AudioPlaybackService] owns the wiring.
 */
class StreamReconnectPolicy(
    private val initialDelayMs: Long = DEFAULT_INITIAL_DELAY_MS,
    private val maxDelayMs: Long = DEFAULT_MAX_DELAY_MS,
    private val minHealthyMs: Long = DEFAULT_MIN_HEALTHY_MS
) {

    companion object {
        const val DEFAULT_INITIAL_DELAY_MS = 1_000L
        const val DEFAULT_MAX_DELAY_MS = 30_000L

        /**
         * How long audio must actually flow before the stream counts as
         * recovered. Comfortably longer than a connect-then-drop cycle.
         */
        const val DEFAULT_MIN_HEALTHY_MS = 10_000L

        /**
         * Beyond this many doublings the delay has already saturated at
         * [maxDelayMs]; the cap keeps the shift from overflowing on a listener
         * who leaves the app open through a long outage.
         */
        private const val MAX_SHIFT = 16
    }

    /** Consecutive reconnect attempts since audio last flowed. */
    var attemptCount: Int = 0
        private set

    /**
     * Delay to wait before the next reconnect attempt, doubling on each
     * consecutive failure up to [maxDelayMs]. Advances the attempt counter.
     */
    fun nextDelayMs(): Long {
        val shift = attemptCount.coerceAtMost(MAX_SHIFT)
        attemptCount++
        val delay = initialDelayMs shl shift
        return if (delay <= 0L) maxDelayMs else delay.coerceAtMost(maxDelayMs)
    }

    /**
     * Whether a dropped stream should be re-established.
     *
     * Retries are unbounded while [userWantsPlayback] holds: a radio stream is
     * expected to come back, and a listener who left the app playing wants audio
     * to resume on its own. Giving up is what the current build does, and it is
     * the bug. When the listener has paused, the backoff is reset so their next
     * tap reconnects immediately rather than inheriting a stale delay.
     */
    fun shouldReconnect(userWantsPlayback: Boolean): Boolean {
        if (!userWantsPlayback) {
            onStreamHealthy()
            return false
        }
        return true
    }

    /** Clears the accumulated backoff outright. */
    fun onStreamHealthy() {
        attemptCount = 0
    }

    /**
     * Records that audio flowed for [playedForMs] before the stream dropped.
     *
     * Only a sustained stretch of playback clears the backoff. Reaching
     * `STATE_READY` is not on its own evidence of a healthy stream: a mount that
     * accepts the connection and closes it a second later would otherwise reset
     * the delay on every cycle and spin the client in a tight reconnect loop.
     */
    fun onPlaybackInterrupted(playedForMs: Long) {
        if (playedForMs >= minHealthyMs) {
            attemptCount = 0
        }
    }
}
