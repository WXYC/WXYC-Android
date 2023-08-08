package com.example.basicmusicplayer


import android.app.appsearch.SearchResult
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.media.MediaPlayer
import android.media.AudioAttributes
import android.view.View
import android.widget.*
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
import kotlinx.coroutines.Runnable
import playback.AudioPlaybackService
import java.util.concurrent.*

// constants used for selecting the range of the playlist to update
// these values are
private const val UPDATE_UPPER_VALUE = 6
private const val UPDATE_LOWER_VALUE = 0
private const val NEW_ENTRY_UPDATE_UPPER_VALUE = 5

// Entrypoint of app that manages main functionality
class PlayerActivity : AppCompatActivity() {
    private lateinit var btnPlayAudio: ImageButton
    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingView: View
    private lateinit var streamImage: ImageView
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
        val playlistRefresh = updatePlaylist()
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
        // initialization of properties
        streamImage = findViewById(R.id.streamImage)
        btnPlayAudio = findViewById(R.id.toggleButton)
        showLoadingView()

        //sets up initial playlist and then shows the playlist when ready
        scope.launch {
            setPlaylistDetailsList(playlistManager.fetchFullPlaylist())
            viewManager.setupRecyclerView(recyclerView, playlistDetailsList)
            showContentView()
        }
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
        btnPlayAudio.visibility = View.GONE
    }

    //shows the content view
    private fun showContentView() {
        loadingView.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
        btnPlayAudio.visibility = View.VISIBLE
    }

    // toggles the audio
    private fun toggleAudio() {
        // handles extra, unnecessary clicks of button
        if (AudioPlaybackService.isPreparing) {
            return
        }
        if (AudioPlaybackService.isPlaying) {
            // if audio is playing, stops the stream
            stopService(Intent(this, AudioPlaybackService::class.java))
            Toast.makeText(this, "Audio has been stopped", Toast.LENGTH_LONG).show()
            streamImage.setImageResource(R.drawable.stream_inactive_short)
            btnPlayAudio.setImageResource(R.drawable.play_button)
        } else {
            // starts the radio stream
            startService(Intent(this, AudioPlaybackService::class.java))
            streamImage.setImageResource(R.drawable.stream_active_short)
            btnPlayAudio.setImageResource(R.drawable.pause_button)
        }

    }

    // updates the playlist
    // this method is not completely perfect; if a new dj was to add multiple entries before a ...
    // .. refresh, a later entry after the updated 6 would be lost
    // however, the case described above is not common and would not effect user experience too much
    // this method is much more efficient than fully refreshing the playlist
    private fun updatePlaylist(): Runnable {
        return Runnable {
            scope.launch {
                // if there is no current playlist, skip this iteration of the updates
                if (playlistDetailsList.size < UPDATE_UPPER_VALUE) {
                    println("playlist is not ready")
                    initializeActivity()
                    return@launch
                }

                // this section compares the first 7 entries of the current list and an updated list
                // if there are differences the first 7 entries will be updated

                // fetches last 7 updated playlist values
                var updatedSubList = playlistManager.fetchLittlePlaylist()
                if (updatedSubList.isNullOrEmpty()) {
                    // switch to null or empty
                    return@launch
                }
                if (updatedSubList.size != UPDATE_UPPER_VALUE) {
                    updatedSubList = updatedSubList.subList(0, UPDATE_UPPER_VALUE)
                }

                // fetches last 7 current playlist values
                val currentSubList = playlistDetailsList.subList(0, UPDATE_UPPER_VALUE)

                // bool values to keep track of update type
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

                    // now that we know there is an update, we fetch the new entries with their art
                    val updatedSublistWithImages = playlistManager.fetchLittlePlaylistWithImages()
                    println("update time")
                    runOnUiThread {
                        // replaces 7 most recent entries with updated order
                        if (editPlaylist) {
                            println("only an edit in the playlist")
                            //clears the last 7 entries of the current playlist
                            playlistDetailsList.subList(UPDATE_LOWER_VALUE, UPDATE_UPPER_VALUE)
                                .clear()
                            //adds the updated, edited 7 entries to the playlist (no new entry)
                            playlistDetailsList.addAll(
                                0, (updatedSublistWithImages.subList(
                                    UPDATE_LOWER_VALUE, UPDATE_UPPER_VALUE
                                ))
                            )
                            // notifies the adapter that the first 7 values have changed
                            recyclerView.adapter?.notifyItemRangeChanged(
                                UPDATE_LOWER_VALUE,
                                UPDATE_UPPER_VALUE
                            )
                        }
                        // adds new entry to the playlist
                        else if (newEntry) {
                            println("addition to the playlist")
                            // clears the last 6 values in the list
                            playlistDetailsList.subList(
                                UPDATE_LOWER_VALUE,
                                NEW_ENTRY_UPDATE_UPPER_VALUE
                            ).clear()
                            // adds the updated 7 values to the list (with new entry)
                            playlistDetailsList.addAll(
                                0, updatedSublistWithImages.subList(
                                    UPDATE_LOWER_VALUE, UPDATE_UPPER_VALUE
                                )
                            )
                            // notifies entire dataset has changed
                            recyclerView.adapter?.notifyDataSetChanged()
                        }
                    }
                } else {
                    println("no update")
                }
            }
        }
    }
}

