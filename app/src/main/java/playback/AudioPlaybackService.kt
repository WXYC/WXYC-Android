package playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionResult
import org.wxyc.wxycapp.MainActivity
import org.wxyc.wxycapp.analytics.AnalyticsEvents
import org.wxyc.wxycapp.analytics.CommandOrigin
import org.wxyc.wxycapp.analytics.PlaybackAttribution
import org.wxyc.wxycapp.analytics.PlaybackDurationTracker
import org.wxyc.wxycapp.analytics.PostHogManager
import java.util.UUID

class AudioPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null
    private lateinit var connectivityManager: ConnectivityManager

    private val reconnectPolicy = StreamReconnectPolicy()
    private val handler = Handler(Looper.getMainLooper())
    private var pendingReconnect: Runnable? = null

    /**
     * Whether the listener currently wants audio. Reconnects are only attempted
     * while this holds, so a deliberate pause is never undone by the recovery path.
     */
    private var userWantsPlayback = false

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    /** Elapsed-realtime stamp of when audio last started flowing; 0 when stopped. */
    private var playingSinceMs = 0L

    /**
     * Times the listen reported as `pause.duration`. Driven from playback *intent*
     * rather than audio flow, matching what iOS reports: a stall mid-listen doesn't
     * split one listen into two.
     */
    private val durationTracker = PlaybackDurationTracker(SystemClock::elapsedRealtime)

    /**
     * The per-listen identifier (`session_id`) shared by a `play` and the `pause` that
     * closes it. Null between listens.
     */
    private var sessionId: String? = null

    /**
     * Where the in-flight play/pause command came from, recorded in
     * `onPlayerCommandRequest` while the issuing controller is still known and consumed
     * by `onPlayWhenReadyChanged`, which only sees a reason code.
     */
    private var pendingOrigin: CommandOrigin? = null

    companion object {
        private const val TAG = "AudioPlaybackService"
        /**
         * The 128 kbps mount, over TLS. This is the same stream the iOS app and
         * the Alexa skill play, and the one the station points listeners at.
         *
         * Not the 320 kbps `wxyc-alt.mp3` mount: at 144 MB/hour it is a poor
         * default over cellular, and Android was its only real consumer.
         */
        private const val STREAM_URL = "https://audio-mp3.ibiblio.org/wxyc.mp3"
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // No notification channel is created here on purpose. MediaSessionService
        // installs a DefaultMediaNotificationProvider when the app doesn't set one,
        // and that provider creates and owns its own IMPORTANCE_LOW channel. The
        // channel this service used to declare was never attached to anything.

        initializePlayer()
        registerNetworkCallback()
    }

    @OptIn(UnstableApi::class)
    private fun initializePlayer() {
        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink? {
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessorChain(
                        DefaultAudioSink.DefaultAudioProcessorChain(
                            data.audio.FftAudioProcessor()
                        )
                    )
                    .build()
            }
        }

        exoPlayer = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            // WAKE_MODE_NETWORK already holds both a partial wake lock and a
            // WIFI_MODE_FULL_HIGH_PERF WifiLock for us (see media3's
            // WifiLockManager), so the service must not manage one itself.
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setHandleAudioBecomingNoisy(true)
            .build()

        exoPlayer?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playingSinceMs = if (isPlaying) SystemClock.elapsedRealtime() else 0L
                updateVisualizerMuteState()
            }

            override fun onVolumeChanged(volume: Float) {
                updateVisualizerMuteState()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                userWantsPlayback = playWhenReady
                reportPlaybackIntentChange(playWhenReady, reason)
                if (!playWhenReady) {
                    // A deliberate pause: drop any queued reconnect and clear the
                    // backoff so the next tap reconnects immediately.
                    cancelPendingReconnect()
                    reconnectPolicy.onStreamHealthy()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        if (reconnectPolicy.attemptCount > 0) {
                            PostHogManager.capture(
                                AnalyticsEvents.STREAM_RECONNECTED,
                                mapOf("attempts" to reconnectPolicy.attemptCount)
                            )
                        }
                        // The backoff is not cleared here: reaching READY only means
                        // the mount accepted us. Sustained playback clears it, in
                        // scheduleReconnect below.
                        cancelPendingReconnect()
                    }

                    Player.STATE_ENDED -> {
                        // A live stream never legitimately ends. Reaching ENDED means
                        // the Icecast server closed the connection and ExoPlayer read
                        // it as a clean end of input, which raises no error.
                        scheduleReconnect("stream_ended")
                    }

                    else -> Unit
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.w(TAG, "Playback error: ${error.errorCodeName}", error)
                PostHogManager.captureError(
                    error = error,
                    context = "AudioPlaybackService.onPlayerError",
                    additionalData = mapOf("error_code" to error.errorCodeName)
                )
                scheduleReconnect(error.errorCodeName)
            }
        })

        exoPlayer?.setMediaItem(MediaItem.fromUri(STREAM_URL))
        exoPlayer?.playWhenReady = false
        exoPlayer?.prepare()

        mediaSession = MediaSession.Builder(this, exoPlayer!!)
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .setCallback(object : MediaSession.Callback {
                override fun onPlayerCommandRequest(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    playerCommand: Int
                ): Int {
                    if (playerCommand == Player.COMMAND_PLAY_PAUSE) {
                        pendingOrigin = originOf(session, controller)
                    }
                    return SessionResult.RESULT_SUCCESS
                }
            })
            .build()
    }

    /**
     * Identifies the controller behind a transport command.
     *
     * This runs while the issuing controller is still known. By the time the player
     * reports the change, the only signal left is a reason code that reads
     * `USER_REQUEST` for the in-app button and the notification alike, so attribution
     * has to be captured here or not at all.
     */
    @OptIn(UnstableApi::class)
    private fun originOf(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): CommandOrigin = when {
        // The notification check must stay ahead of the package comparison and is not
        // reorderable: the media notification controller runs in this app's own
        // process and reports this app's package name, so the package test alone
        // would read every notification tap as an in-app tap.
        session.isMediaNotificationController(controller) -> CommandOrigin.MEDIA_NOTIFICATION
        controller.packageName == packageName -> CommandOrigin.IN_APP
        else -> CommandOrigin.OTHER_CONTROLLER
    }

    /**
     * Emits `play` / `pause` for a change in playback intent.
     *
     * Lives here rather than in the play/pause button's view model so that playback
     * started from the notification, a headset button, or the reconnect path is counted
     * too. Instrumenting only the in-app control left `source` with a single value and
     * left Android's `play` count meaning something narrower than the identically-named
     * iOS event (WXYC-Android#48).
     *
     * Driven by `playWhenReady` — intent — rather than `isPlaying`, so a stall or a
     * rebuffer doesn't manufacture a spurious pause/play pair.
     */
    private fun reportPlaybackIntentChange(playWhenReady: Boolean, reason: Int) {
        val attribution = PlaybackAttribution.resolve(reason, pendingOrigin)
        pendingOrigin = null

        if (playWhenReady) {
            sessionId = UUID.randomUUID().toString()
            durationTracker.onPlaybackStarted()
            PostHogManager.capturePlay(attribution, sessionId)
        } else {
            // No session means nothing was ever started — e.g. the initial
            // playWhenReady = false during setup. Reporting a pause there would invent
            // a listen that never happened.
            if (sessionId == null) return

            PostHogManager.capturePause(attribution, durationTracker.durationSeconds(), sessionId)
            durationTracker.onPlaybackStopped()
            sessionId = null
        }
    }

    /**
     * Queues a reconnect after the policy's backoff. Any previously queued attempt
     * is dropped so overlapping signals (an error followed by ENDED, say) can't
     * stack up into a burst of requests.
     */
    private fun scheduleReconnect(reason: String) {
        val playedForMs =
            if (playingSinceMs == 0L) 0L else SystemClock.elapsedRealtime() - playingSinceMs
        playingSinceMs = 0L
        reconnectPolicy.onPlaybackInterrupted(playedForMs)

        if (!reconnectPolicy.shouldReconnect(userWantsPlayback)) {
            return
        }

        cancelPendingReconnect()

        val delayMs = reconnectPolicy.nextDelayMs()
        Log.i(TAG, "Stream dropped ($reason); reconnecting in ${delayMs}ms")
        PostHogManager.capture(
            AnalyticsEvents.STREAM_ERROR,
            mapOf(
                "reason" to reason,
                "attempt" to reconnectPolicy.attemptCount,
                "delay_ms" to delayMs,
                "played_for_ms" to playedForMs
            )
        )

        val reconnect = Runnable {
            pendingReconnect = null
            reconnectNow()
        }
        pendingReconnect = reconnect
        handler.postDelayed(reconnect, delayMs)
    }

    /**
     * Rebuilds the media item and re-prepares. A fresh item is required because
     * a player sitting in ENDED is parked at the end of the old, finished source.
     */
    private fun reconnectNow() {
        val player = exoPlayer ?: return
        if (!userWantsPlayback) return

        // Deliberately does not stamp an attribution. This path only runs while
        // userWantsPlayback holds — i.e. playWhenReady is already true — so play()
        // is a no-op that reports no intent change, and a recovered drop emits no
        // play/pause at all. That is the intended shape: a stall must not split one
        // listen into two. Marking an origin here would also be actively unsafe,
        // because a controller command is queued separately from the callback that
        // records its origin (MediaSessionStub queues, flushCommandQueue drains), so
        // a marker written by this timer can be consumed by a user's pause that was
        // already in flight, reporting it as automatic.
        player.setMediaItem(MediaItem.fromUri(STREAM_URL))
        player.prepare()
        player.play()
    }

    private fun cancelPendingReconnect() {
        pendingReconnect?.let { handler.removeCallbacks(it) }
        pendingReconnect = null
    }

    /**
     * Retries as soon as connectivity returns rather than waiting out the backoff.
     * The MediaPlayer-based build watched CONNECTIVITY_ACTION for this; the
     * ExoPlayer rewrite kept the ConnectivityManager field but dropped the watch.
     */
    private fun registerNetworkCallback() {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                handler.post {
                    if (userWantsPlayback && exoPlayer?.isPlaying != true) {
                        cancelPendingReconnect()
                        reconnectPolicy.onStreamHealthy()
                        reconnectNow()
                    }
                }
            }
        }
        networkCallback = callback
        try {
            connectivityManager.registerDefaultNetworkCallback(callback)
        } catch (e: SecurityException) {
            Log.w(TAG, "Unable to watch connectivity; falling back to backoff only", e)
            networkCallback = null
        }
    }

    private fun updateVisualizerMuteState() {
        val isMuted = (exoPlayer?.volume ?: 1f) == 0f
        data.audio.AudioVisualizerState.isMuted = isMuted
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    /**
     * Keeps the service in the foreground while a reconnect is pending.
     *
     * media3 decides foreground state from `shouldRunInForeground`, which requires
     * the player to be READY or BUFFERING. A dropped stream parks it in ENDED (or
     * IDLE after an error), so media3 would call `stopForeground` — the lock-screen
     * player disappears and Android is then free to kill the service, taking the
     * queued reconnect with it. That is the symptom users report. The listener still
     * wants audio during recovery, so hold the foreground until it succeeds or they
     * pause.
     */
    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        val recovering = userWantsPlayback && pendingReconnect != null
        super.onUpdateNotification(session, startInForegroundRequired || recovering)
    }

    override fun onDestroy() {
        cancelPendingReconnect()
        networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
        networkCallback = null
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
