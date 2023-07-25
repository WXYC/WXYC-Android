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

import com.bumptech.glide.Glide
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import java.util.concurrent.*


// Entrypoint of app that manages main functionality
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
    private val playlistDetailsList: MutableList<PlaylistDetails> = CopyOnWriteArrayList()


    // initializes the activity and sets the layout
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        initializeActivity()


        //refreshes the playlist every 30 seconds. will abstract this into its own function
        val playlistRefresh = Runnable {
            scope.launch {
                if (playlistDetailsList.size < 6){
                    println("playlist is not ready")
                    initializeActivity()
                    return@launch
                }
                // this section compares the first 7 entries of the current list and an updates

                // updated playlist values
                var updatedSubList = playlistManager.fetchLittlePlaylist()

                if (updatedSubList.size != 6){
                    updatedSubList = updatedSubList.subList(0,6)
                }

                val currentSubList = playlistDetailsList.subList(0, 6)

                var editPlaylist = false
                var newEntry = false


                //  checks if the lists are the same
                if (!compareLists(updatedSubList, currentSubList)) {
                    // this determined if the two sublists are different.  now we need to check if
                    // an entry was added or there was an edit to the playlist (with no added entry)

                    // checks if the content in the lists are the same i.e. just an edit in order
                    if (compareListContent(updatedSubList, currentSubList)) {
                        editPlaylist = true
                    }
                    // new entry
                    else {
                        newEntry = true
                    }

                    //COMPARISON PART OVER

                    val updatedSublistWithImages = playlistManager.fetchLittlePlaylistWithImages()
                    println("update time")
                    runOnUiThread {
                        // replaces 7 most recent entries with updated order
                        if (editPlaylist) {
                            println("only an edit in the playlist")
                            playlistDetailsList.subList(0, 6).clear()
                            playlistDetailsList.addAll(0, (updatedSublistWithImages.subList(0, 6)))
                            recyclerView.adapter?.notifyItemRangeChanged(0, 6)
                        }
                        // adds new entry to the playlist
                        else if (newEntry) {
                            println("addition to the playlist")
                            //val rangeToRemove = minOf(5, playlistDetailsList.size)
                            playlistDetailsList.subList(0, 5).clear()
                            playlistDetailsList.addAll(0, updatedSublistWithImages.subList(0, 6))
                            recyclerView.adapter?.notifyDataSetChanged()
                        }
                    }
                } else {
                    println("no update")
                }
            }
        }
        executor.scheduleAtFixedRate(playlistRefresh, 20, 30, TimeUnit.SECONDS)

        // listens for button to be clicked
        btnPlayAudio.setOnClickListener {
            toggleAudio()
        }
    }

    private fun initializeActivity() {
        // LoadingView used while the recyclerView is preparing
        loadingView = findViewById(R.id.loading_screen)
        recyclerView = findViewById(R.id.recycler_view)
        showLoadingView()

        //sets up initial playlist and then shows the playlist when ready
        scope.launch {
            setPlaylistDetailsList(playlistManager.fetchFullPlaylist())
            viewManager.setupRecyclerView(recyclerView, playlistDetailsList)
            showContentView()
        }


        // initialization of properties
        loadingIndicator = findViewById(R.id.loading_indicator)
        loadingIndicator.visibility = View.INVISIBLE
        btnPlayAudio = findViewById(R.id.btnPlayAudio)

    }

    // checks if lists are the same
    private fun compareLists(
        listOne: List<PlaylistDetails>,
        listTwo: List<PlaylistDetails>
    ): Boolean {
        if (listOne.size != listTwo.size) {
            println("list comparison: FALSE (different size lists)")
            return false
        }
        for (i in listOne.indices) {
            val playCutIdOne = listOne[i].id
            val playCutIdTwo = listTwo[i].id
            if (playCutIdOne != playCutIdTwo) {
                return false
            }
        }
        return true
    }

    // checks to see if the values in the list the same regardless of order
    private fun compareListContent(
        listOne: List<PlaylistDetails>,
        listTwo: List<PlaylistDetails>
    ): Boolean {
        val setOne = listOne.map { it.id }.toSet()
        println(setOne)
        val setTwo = listTwo.map { it.id }.toSet()
        println(setTwo)
        if (setOne != setTwo) {
            return false
        }
        return true
    }


    // setter method for playlistDetailList
    private fun setPlaylistDetailsList(list: List<PlaylistDetails>) {
        playlistDetailsList.clear()
        playlistDetailsList.addAll(list)
    }


    // shows the loading view
    private fun showLoadingView() {
        loadingView.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
    }

    //shows the content view
    private fun showContentView() {
        loadingView.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
    }

    // toggles the audio
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


    // plays the stream of the radi
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

