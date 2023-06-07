package com.example.basicmusicplayer


import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.media.MediaPlayer
import android.media.AudioAttributes
import android.widget.ProgressBar
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager

import data.JsonImporter
import data.PlaylistAdapter

import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit


//define main activity class and define properties
class PlayerActivity : AppCompatActivity() {
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var btnPlayAudio: Button
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var recyclerView: RecyclerView
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    // lists to store song details
    var songDetailsList = mutableListOf<Pair<String, String>>()
    var updatedSongDetailsList = mutableListOf<Pair<String, String>>()


    // initializes the activity and sets the layout
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        //sets up initial playlist
        playlistSetUp()


        // initialization of properties
        loadingIndicator = findViewById(R.id.loading_indicator)
        loadingIndicator.visibility = View.INVISIBLE
        btnPlayAudio = findViewById(R.id.btnPlayAudio)

        //refreshes the playlist every 30 seconds
        val playlistRefresh = Runnable {
            playlistUpdater()
        }
        executor.scheduleAtFixedRate(playlistRefresh, 5, 30, TimeUnit.SECONDS)


        // listens for button to be clicked
        btnPlayAudio.setOnClickListener {
            toggleAudio()
        }
    }

    private fun playlistSetUp() {
        // initialize the JsonImporter class
        val jsonImporter = JsonImporter()
        // countdownlatch initialized to wait for callback to complete
        val latch = CountDownLatch(1) // this can be adapted to more advanced asynch programming if necessary


        val callback: (MutableList<Pair<String, String>>) -> Unit = { songDetails ->
            songDetailsList.addAll(songDetails)
            // Process the song details here
            latch.countDown()
        }

        jsonImporter.fillPlaylist(callback)

        // Wait for the callback to complete
        latch.await()

        // RecyclerView set-up
        recyclerView = findViewById(R.id.recycler_view)
        recyclerView.adapter = PlaylistAdapter(songDetailsList)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.setHasFixedSize(true)  //change back to true

        return

    }


    private fun toggleAudio() {
        // handles extra, unnecessary clicks of button
        if (loadingIndicator.visibility == View.VISIBLE) {
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
                Toast.makeText(applicationContext, "Audio started playing", Toast.LENGTH_LONG)
                    .show()
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


    private fun playlistUpdater() {
        val jsonImporter = JsonImporter()
        // countdownlatch initialized
        val latch =
            CountDownLatch(1) // this can be adapted to more advanced asynch programming if necessary
        // list to store song details
        if (updatedSongDetailsList.isNotEmpty()) {
            updatedSongDetailsList.clear()
        }
        val callback: (MutableList<Pair<String, String>>) -> Unit = { songDetails ->
            updatedSongDetailsList.addAll(songDetails)
            // Process the song details here
            latch.countDown()
        }
        jsonImporter.fillPlaylist(callback)
        // Wait for the callback to complete
        latch.await()

        if (updatedSongDetailsList[0] != songDetailsList[0]) {
            val newSong = updatedSongDetailsList[0]
            songDetailsList.add(0, newSong)
            //runs the adapter on the main thread
            runOnUiThread {
                // Notify the adapter about the data change
                for (i in 0 until songDetailsList.size) {
                    recyclerView.adapter?.notifyItemChanged(i)
                }
            }
            return
        }
    }
}