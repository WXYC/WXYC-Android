package com.example.basicmusicplayer

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.media.MediaPlayer
import android.media.AudioAttributes
import android.widget.ProgressBar
import android.view.View
import android.widget.Button
import android.widget.Toast


//define main activity class and define properties
class PlayerActivity : AppCompatActivity() {
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var btnPlayAudio : Button
    private var mediaPlayer : MediaPlayer? = null



    // initializes the activity and sets the layout
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        // initialization of properties
        loadingIndicator = findViewById(R.id.loading_indicator)
        loadingIndicator.visibility = View.INVISIBLE
        btnPlayAudio = findViewById(R.id.btnPlayAudio)

        // listens for button to be clicked
        btnPlayAudio.setOnClickListener {
            toggleAudio()
        }
    }


    private fun toggleAudio() {
        // handles extra, unnecessary clicks of button
        if (loadingIndicator.visibility == View.VISIBLE){
            return
        }
        //stops and releases player if it is playing
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.stop()
            mediaPlayer?.reset()
            mediaPlayer?.release()
            mediaPlayer = null
            Toast.makeText(this, "Audio has been stopped", Toast.LENGTH_LONG).show()
        } else {
            playRadio()
        }
    }


    private fun playRadio() {
        val wxycURL = "http://audio-mp3.ibiblio.org:8000/wxyc-alt.mp3"

        loadingIndicator.visibility = View.VISIBLE

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
                loadingIndicator.visibility = View.GONE // Hide the loading indicator
                Toast.makeText(applicationContext, "Audio started playing", Toast.LENGTH_LONG).show()
            }
            //initiates prep process
            prepareAsync()
        }
    }


// ensures it releases the mediaPlayer when app is closed?
    override fun onStop() {
        super.onStop()
        releaseMediaPlayer()
    }

    private fun releaseMediaPlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
    }


}