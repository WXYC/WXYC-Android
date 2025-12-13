package playback


import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import org.wxyc.wxycapp.PlayerActivity
import org.wxyc.wxycapp.R
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager

// class that handles playback of radio
class AudioPlaybackService : Service() {

    // companion objects to track whether the radio is playing
    companion object {
        var isPlaying: Boolean = false
        var isPreparing: Boolean = false
        var isMuted: Boolean = false
        var hasConnection: Boolean = true
    }

    private lateinit var audioManager: AudioManager
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var broadcastReceiver: BroadcastReceiver
    private var exoPlayer: androidx.media3.exoplayer.ExoPlayer? = null
    private var wifiLock: WifiManager.WifiLock? = null

    // method is called when the service is created. sets up audio and initiates radio playback
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
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

        // Register network connectivity receiver
        setUpConnectionLossReceiver()
    }

    // function to see if app is connected to network
    private fun isNetworkConnected(): Boolean {
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }


    // initializes the media player
    @RequiresApi(Build.VERSION_CODES.O)
    private fun playRadio() {
        val wxycURL = "http://audio-mp3.ibiblio.org:8000/wxyc-alt.mp3"
        isPreparing = true

        // Acquire WiFi lock to keep WiFi radio active during streaming
        if (wifiLock == null) {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "WXYC:WifiLock")
        }
        wifiLock?.acquire()

        if (exoPlayer == null) {
            exoPlayer = androidx.media3.exoplayer.ExoPlayer.Builder(this)
                .setAudioAttributes(
                    androidx.media3.common.AudioAttributes.Builder()
                        .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                        .build(),
                    true // handleAudioFocus
                )
                .setWakeMode(androidx.media3.common.C.WAKE_MODE_NETWORK)
                .setHandleAudioBecomingNoisy(true)
                .build()

            exoPlayer?.addListener(object : androidx.media3.common.Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        androidx.media3.common.Player.STATE_READY -> {
                            println("ExoPlayer STATE_READY")
                            isPreparing = false
                            // isPlaying state is handled by onIsPlayingChanged
                        }
                        androidx.media3.common.Player.STATE_BUFFERING -> {
                            println("ExoPlayer STATE_BUFFERING")
                            isPreparing = true
                        }
                        androidx.media3.common.Player.STATE_ENDED -> {
                            println("ExoPlayer STATE_ENDED")
                            isPreparing = false
                        }
                        androidx.media3.common.Player.STATE_IDLE -> {
                            println("ExoPlayer STATE_IDLE")
                            isPreparing = false
                        }
                    }
                }

                override fun onIsPlayingChanged(isPlayingState: Boolean) {
                    Companion.isPlaying = isPlayingState
                    if (isPlayingState) {
                        println("ExoPlayer playing")
                        setActiveImagesInPlayerActivity()
                    } else {
                        println("ExoPlayer paused/stopped")
                        // Don't set inactive immediately unless it's a permanent stop/pause, 
                        // but for radio stream, pause usually means stop.
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    println("ExoPlayer Error: ${error.message}")
                    isPreparing = false
                    releaseExoPlayer()
                }
            })
        }

        val mediaItem = androidx.media3.common.MediaItem.fromUri(wxycURL)
        exoPlayer?.setMediaItem(mediaItem)
        exoPlayer?.prepare()
        exoPlayer?.play()
    }
    
    //called when service begins. creates notification that service is running
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isNetworkConnected()) {
            println("there was no network to begin with")
            // Handle loss of network connectivity when starting the service
            Toast.makeText(this, "No Network Connection", Toast.LENGTH_SHORT).show()
            setInactiveImagesInPlayerActivity()
            stopSelf() // Stop the service if there's no network
            return START_NOT_STICKY
        }
        if (intent != null) {
            val action = intent.getStringExtra("action")
            if (action != null) {
                when (action) {
                    "mute" -> muteAudio()
                    "unmute" -> unmuteAudio()
                    "startUnmuted" -> playRadio()
                }
            }
            else if (exoPlayer == null){
                playMutedRadio()
            }
        }

        val notification = createNotification()
        startForeground(1, notification)
        return START_STICKY
    }

    // ends the radio stream when audio is toggled
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onDestroy() {
        println("made it to on destroy")
        releaseExoPlayer()
        unregisterReceiver(broadcastReceiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    // creates notification for app audio service
    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, PlayerActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val channelId = "AudioPlaybackChannel"
        // Channel creation moved to onCreate or handled if < O not supported generally for this part
        
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(null) // Remove the content title to make it more minimal
            .setContentText(null) // Remove the content text to make it more minimal
            .setSmallIcon(R.drawable.music_note)
            .setContentIntent(pendingIntent)
            .setSound(null) // Mute the notification sound
            .setPriority(NotificationCompat.PRIORITY_LOW) // Set priority to LOW to make the notification less intrusive
            .build()
    }

    // releases media player
    private fun releaseExoPlayer() {
        println("releasing ExoPlayer")
        setInactiveImagesInPlayerActivity()
        exoPlayer?.run {
            stop()
            release()
        }
        exoPlayer = null
        isPlaying = false // Set isPlaying to false when audio playback is destroyed
        releaseWifiLock()
    }

    private fun releaseWifiLock() {
        if (wifiLock?.isHeld == true) {
            wifiLock?.release()
        }
    }

    // Audio focus listener removed as ExoPlayer handles it internally with .setAudioAttributes(..., true)

    private fun setActiveImagesInPlayerActivity() {
        val intent = Intent("UpdateImagesIntent").apply {
            putExtra("command", "setActive")
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun setInactiveImagesInPlayerActivity() {
        val intent = Intent("UpdateImagesIntent").apply {
            putExtra("command", "setInactive")
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    fun muteAudio() {
        println("made it to mute")
        exoPlayer?.volume = 0f
        isMuted = true
    }

    fun unmuteAudio() {
        println("made it to unmute")
        exoPlayer?.volume = 1f
        isMuted = false
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun playMutedRadio(){
        playRadio()
        muteAudio()
        isMuted = true
    }

    private fun setUpConnectionLossReceiver(){
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (!isNetworkConnected()) {
                    // Handle loss of network connectivity while playing
                    Toast.makeText(context, "Network connection lost", Toast.LENGTH_SHORT).show()
                    releaseExoPlayer()
                    hasConnection = false
                }
                else {
                    println("there is a connection")
                    hasConnection = true
                }
            }
        }
        val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        registerReceiver(broadcastReceiver, filter)
    }
}
