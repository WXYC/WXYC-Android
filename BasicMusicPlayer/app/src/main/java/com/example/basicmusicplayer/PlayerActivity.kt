package com.example.basicmusicplayer


import android.app.appsearch.SearchResult
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.media.MediaPlayer
import android.media.AudioAttributes
import android.widget.ProgressBar
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager

import androidx.recyclerview.widget.RecyclerView
import data.*
import kotlinx.coroutines.launch
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

import com.bumptech.glide.Glide
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers


//define main activity class and define properties
class PlayerActivity : AppCompatActivity() {
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var btnPlayAudio: Button
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingView: View
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private var playlistManager = PlaylistManager()
    private val scope = CoroutineScope(Dispatchers.Main)
    private val viewManager = ViewManager()

    private var updatedPlaylistDetailsList: MutableList<PlaylistDetails> = mutableListOf()

    private lateinit var playlistDetailsList: MutableList<PlaylistDetails>







    // initializes the activity and sets the layout
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        // LoadingView used while the recyclerView is preparing
        loadingView = findViewById(R.id.loading_screen)
        recyclerView = findViewById(R.id.recycler_view)


        showLoadingView()


        //sets up initial playlist and then shows the playlist when ready

        scope.launch{
            playlistDetailsList = playlistManager.fetchPlaylist()
            viewManager.setupRecyclerView(recyclerView, playlistDetailsList)
            showContentView()
            println("here are the playlist details right after launch")
            println(playlistDetailsList)
        }






        // initialization of properties
        loadingIndicator = findViewById(R.id.loading_indicator)
        loadingIndicator.visibility = View.INVISIBLE
        btnPlayAudio = findViewById(R.id.btnPlayAudio)


        //refreshes the playlist every 30 seconds. should be a prettier way to do this, like just put it inside
        val playlistRefresh = Runnable {
            scope.launch {
                if (updatedPlaylistDetailsList.isNotEmpty()){
                    updatedPlaylistDetailsList.clear()
                    println("cleared it")
                }

                //here lies the problem: on the first run it is populated but after
                println("here are the 'og' playlist details right at the first checkpoint!!")
                println(playlistDetailsList)
                updatedPlaylistDetailsList = playlistManager.fetchPlaylist()
                println("current view playlist")
                println(playlistDetailsList)
                println("updated playlist")
                println(updatedPlaylistDetailsList)

                if (playlistDetailsList != updatedPlaylistDetailsList){
                    println("they are different!")
                }
            }
        }

        executor.scheduleAtFixedRate(playlistRefresh, 5, 10, TimeUnit.SECONDS)



        // listens for button to be clicked
        btnPlayAudio.setOnClickListener {
            toggleAudio()
        }
    }


    private fun showLoadingView() {
        loadingView.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
    }

    private fun showContentView() {
        loadingView.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
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

}

