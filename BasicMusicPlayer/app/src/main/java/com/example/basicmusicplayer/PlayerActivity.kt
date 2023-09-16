package com.example.basicmusicplayer


import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.RecyclerView
import data.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    private lateinit var imageUpdateReceiver: BroadcastReceiver
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private var playlistManager = PlaylistManager()
    private val scope = CoroutineScope(Dispatchers.Main)
    private val viewManager = ViewManager()
    private val playlistDetailsList: MutableList<PlaylistDetails> = CopyOnWriteArrayList()
    private var muteCounter = 0
   // private lateinit var binding: ActivityPlayerBinding

    // initializes the activity and sets the layout
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        initializeActivity()

        // Register a BroadcastReceiver to update image resources
        setUpImageUpdateReceiver()

        val playlistRefresh = updatePlaylist()
        val muteStatus = checkMuteStatus()

        //refreshes the playlist every 30 seconds
        executor.scheduleAtFixedRate(playlistRefresh, 20, 30, TimeUnit.SECONDS)
        // checks to see if stream has been muted every minute. if stream has been muted for...
        // 1 minute, it releases the player
        executor.scheduleAtFixedRate(muteStatus, 30, 30, TimeUnit.SECONDS)


        // listens for button to be clicked
        btnPlayAudio.setOnClickListener {
            toggleAudio()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopService(Intent(this, AudioPlaybackService::class.java))
        LocalBroadcastManager.getInstance(this).unregisterReceiver(imageUpdateReceiver)
        executor.shutdown()
    }

    private fun initializeActivity() {
        // LoadingView used while the recyclerView is preparing
        loadingView = findViewById(R.id.loading_screen)
        recyclerView = findViewById(R.id.recycler_view)
        // initialization of properties
        streamImage = findViewById(R.id.streamImage)
        btnPlayAudio = findViewById(R.id.toggleButton)
        showLoadingView()

        startService(Intent(this, AudioPlaybackService::class.java))
        setInactiveStream()

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
        val setTwo = listTwo.map { it.id }.toSet()
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
        println("made it to toggle audio")
        if (AudioPlaybackService.isPreparing) {
            println("said it was preparing")
            return
        }
        // if audio stream is not running, we need to start the stream again
        if (!AudioPlaybackService.isPlaying) {
            println("sensed it was not playing")
            // case for stream is not playing but it appears to still be active, essentially resets things
            if (!AudioPlaybackService.isMuted){
                setInactiveStream()
            }
            // audio stream is not playing, but we want to start the stream again
            else{
                // starts the stream if there is internet connection
                if (AudioPlaybackService.hasConnection) {
                    val audioServiceIntent = Intent(this, AudioPlaybackService::class.java)
                    audioServiceIntent.putExtra("action", "startUnmuted")
                    startService(audioServiceIntent)
                    setActiveStream()
                    Toast.makeText(applicationContext, "Loading WXYC stream", Toast.LENGTH_LONG)
                        .show()
                }
                // case where there is no internet
                else {
                    Toast.makeText(applicationContext, "No Internet Connection", Toast.LENGTH_LONG)
                        .show()
                }
            }
        }
        // stream is running but just muted (paused) or unmuted (playing)
        else {
            println("sensed it was muted")
            // audio is "playing", so we pause "mute" the stream
            if (!AudioPlaybackService.isMuted) {
                println("made it to intent part")
                val audioServiceIntent = Intent(this, AudioPlaybackService::class.java)
                audioServiceIntent.putExtra("action", "mute")
                startService(audioServiceIntent)
                setInactiveStream()
            }
            // audio is "paused", so we play "unmute" the stream
            else {
                val audioServiceIntent = Intent(this, AudioPlaybackService::class.java)
                audioServiceIntent.putExtra("action", "unmute")
                startService(audioServiceIntent)
                setActiveStream()
            }
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
                // fetches last 7 updated playlist values
                var updatedSubList = playlistManager.fetchLittlePlaylist()
                // bool value to keep track of update type
                var newEntry = false

                // if there is no current playlist, skip this iteration of the updates
                if (playlistDetailsList.size < UPDATE_UPPER_VALUE) {
                    initializeActivity()
                    return@launch
                }
                if (updatedSubList.isNullOrEmpty()) {
                    return@launch
                }

                // this section compares the first 7 entries of the current list and an updated list
                // if there are differences the first 7 entries will be updated
                if (updatedSubList.size != UPDATE_UPPER_VALUE) {
                    updatedSubList = updatedSubList.subList(0, UPDATE_UPPER_VALUE)
                }

                // fetches last 7 current playlist values
                val currentSubList = playlistDetailsList.subList(0, UPDATE_UPPER_VALUE)

                //  checks if the lists are the same
                if (!compareLists(updatedSubList, currentSubList)) {
                    // this determined if the two sublists are different.  now we need to check if
                    // an entry was added or there was an edit to the playlist (with no added entry)

                    // checks if the content in the lists are the same i.e. just an edit in order
                    newEntry = !compareListContent(updatedSubList, currentSubList)
                    //COMPARISON PART OVER
                    fetchUpdatedPlaylistEntries(newEntry)
                } else {
                    println("no update")
                }
            }
        }
    }

    private suspend fun fetchUpdatedPlaylistEntries(newEntry: Boolean) {
        val updatedSublistWithImages = playlistManager.fetchLittlePlaylistWithImages()
        println("update time")
        runOnUiThread {
            // replaces 7 most recent entries with updated order
            if (!newEntry) {
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
    }

    // function to release audio if the stream has been muted/paused for an extended amount of time
    private fun checkMuteStatus(): Runnable {
        return Runnable {
            if (AudioPlaybackService.isPlaying){
                if (AudioPlaybackService.isMuted){
                    muteCounter += 1
                }
                if (muteCounter == 2){
                    muteCounter = 0
                    stopService(Intent(this, AudioPlaybackService::class.java))
                }
            }
        }
    }

    // receiver for updates from AudioPlaybackService regarding stream images
    private fun setUpImageUpdateReceiver() {
        imageUpdateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val command = intent?.getStringExtra("command")
                if (command == "setInactive") {
                    setInactiveStream()
                }
                if (command == "setActive") {
                    setActiveStream()
                }
            }
        }
        val intentFilter = IntentFilter("UpdateImagesIntent")
        LocalBroadcastManager.getInstance(this).registerReceiver(imageUpdateReceiver, intentFilter)
    }

    // sets active stream images
    private fun setActiveStream() {
        streamImage.setImageResource(R.drawable.nobackground_active_stream)
        btnPlayAudio.setImageResource(R.drawable.pause_button)
        AudioPlaybackService.isMuted = false
    }

    // sets inactive stream images
    private fun setInactiveStream() {
        streamImage.setImageResource(R.drawable.nobackground_inactive_stream)
        btnPlayAudio.setImageResource(R.drawable.ios_play_button)
        AudioPlaybackService.isMuted = true
    }
}

