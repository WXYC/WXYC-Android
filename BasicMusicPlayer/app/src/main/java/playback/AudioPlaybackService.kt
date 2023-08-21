package playback


import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import com.example.basicmusicplayer.PlayerActivity
import com.example.basicmusicplayer.R
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
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
    //declares class properties for managing audio focus, attributes, and the media player
    private lateinit var audioManager: AudioManager
    private lateinit var audioFocusRequest: AudioFocusRequest
    private lateinit var audioAttributes: AudioAttributes
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var broadcastReceiver: BroadcastReceiver
    private var mediaPlayer: MediaPlayer? = null

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

            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            // Create an AudioFocusRequest
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setOnAudioFocusChangeListener(afChangeListener)
                .build()
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
        //media player created and initialized
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(audioAttributes)
            setDataSource(wxycURL)
            //set to handle event when audio is prepared
            setOnPreparedListener { mp ->
                mp.start()
                Companion.isPlaying = true
                println("STARTED RADIO FROM SCRATCH")
                isPreparing = false
            }
            // Set up the error listener to handle any errors during media preparation
            setOnErrorListener { mp, what, extra ->
                isPreparing = false
                releaseMediaPlayer() // Release the media player if an error occurs
                false
            }
            val result = audioManager.requestAudioFocus(audioFocusRequest)
            //initiates prep process
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                // Start playback
                prepareAsync()
            }
        }
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
            else if (mediaPlayer == null){
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
        mediaPlayer?.apply {
            setOnPreparedListener(null)
            setOnErrorListener(null)
            stop()
            reset()
            release()
        }
        mediaPlayer = null
        isPlaying = false // Set isPlaying to false when audio playback is destroyed
        audioManager.abandonAudioFocusRequest(audioFocusRequest)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                channelId,
                "Music Player",
                NotificationManager.IMPORTANCE_LOW // Use LOW importance to make the notification less intrusive
            )
            // Set the audio attributes to null to mute sound and vibration
            channel.setSound(null, null)
            notificationManager.createNotificationChannel(channel)
        }

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
    private fun releaseMediaPlayer() {
        println("released media player")
        setInactiveImagesInPlayerActivity()
        mediaPlayer?.apply {
            setOnPreparedListener(null)
            setOnErrorListener(null)
            stop()
            reset()
            release()
        }
        mediaPlayer = null
        isPlaying = false // Set isPlaying to false when audio playback is destroyed
    }

    // used to monitor changes in audio focus state and adjust behavior accordingly
    @RequiresApi(Build.VERSION_CODES.O)
    private val afChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            // when app has lost long-term audio focus (another app or system is taking over audio)
            AudioManager.AUDIOFOCUS_LOSS -> {
                println("full loss")
                // Release media player and stop playback
                setInactiveImagesInPlayerActivity()
                onDestroy()
                isPlaying = false
            }
            // temporary lost audio focus ex. phone call / notification
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                println("temporary loss")
                // Pause playback temporarily
                mediaPlayer?.pause()
                isPlaying = false
            }
            // app regains audio focus
            AudioManager.AUDIOFOCUS_GAIN -> {
                println("regained")
                // Resume playback
                mediaPlayer?.start()
                isPlaying = true
            }
        }
    }

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
        mediaPlayer?.setVolume(0f, 0f)
        isMuted = true
    }

    fun unmuteAudio() {
        println("made it to unmute")
        mediaPlayer?.setVolume(1f, 1f)
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
                    releaseMediaPlayer()
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
