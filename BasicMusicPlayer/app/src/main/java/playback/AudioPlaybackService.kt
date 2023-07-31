package playback


import android.app.*
import com.example.basicmusicplayer.PlayerActivity
import com.example.basicmusicplayer.R
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.view.View
import android.widget.Toast
import androidx.core.app.NotificationCompat

// class that handles playback of radio
class AudioPlaybackService : Service() {

    // companion object to track whether the radio is playing
    companion object {
        var isPlaying: Boolean = false
    }

    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate() {
        super.onCreate()
        isPlaying = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "AudioPlaybackChannel"
            val channel = NotificationChannel(
                channelId,
                "Music Player",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
        playRadio()
    }

    private fun playRadio() {
        val wxycURL = "http://audio-mp3.ibiblio.org:8000/wxyc-alt.mp3"
        //media player created and initialized
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            setDataSource(wxycURL)
            //set to handle event when audio is prepared
            setOnPreparedListener { mp ->
                mp.start()
                Toast.makeText(applicationContext, "Audio started playing", Toast.LENGTH_LONG).show()
                AudioPlaybackService.isPlaying = true
            }

            // Set up the error listener to handle any errors during media preparation
            setOnErrorListener { mp, what, extra ->
                releaseMediaPlayer() // Release the media player if an error occurs
                false
            }

            //initiates prep process
            prepareAsync()
        }
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(1, notification)
        return START_STICKY
    }

    // ends the radio stream when audio is toggled
    override fun onDestroy() {
        mediaPlayer?.run {
            setOnPreparedListener(null)
            setOnErrorListener(null)
            stop()
            release()
            mediaPlayer = null
        }
        isPlaying = false // Set isPlaying to false when audio playback is paused
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }


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

    private fun releaseMediaPlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
