package playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
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
import org.wxyc.wxycapp.MainActivity
import org.wxyc.wxycapp.R

class AudioPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private lateinit var connectivityManager: ConnectivityManager

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "AudioPlaybackChannel"
            val channel = NotificationChannel(
                channelId,
                "WXYC Stream",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        initializePlayer()
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
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setHandleAudioBecomingNoisy(true)
            .build()

        exoPlayer?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                 if (isPlaying) {
                    acquireWifiLock()
                } else {
                    releaseWifiLock()
                }
                updateVisualizerMuteState()
            }
            
            override fun onVolumeChanged(volume: Float) {
                updateVisualizerMuteState()
            }

            override fun onPlayerError(error: PlaybackException) {
                // Auto-retry or just stop. ExoPlayer usually stops on error.
            }
        })

        val mediaItem = MediaItem.fromUri("http://audio-mp3.ibiblio.org:8000/wxyc-alt.mp3")
        exoPlayer?.setMediaItem(mediaItem)
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
            .build()
    }

    private fun updateVisualizerMuteState() {
        val isMuted = (exoPlayer?.volume ?: 1f) == 0f
        data.audio.AudioVisualizerState.isMuted = isMuted
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        releaseWifiLock()
        super.onDestroy()
    }
    
    private fun acquireWifiLock() {
        if (wifiLock == null) {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "WXYC:WifiLock")
        }
        wifiLock?.acquire()
    }

    private fun releaseWifiLock() {
        if (wifiLock?.isHeld == true) {
            wifiLock?.release()
        }
    }
}
