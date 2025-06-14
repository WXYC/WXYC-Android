package com.example.basicmusicplayer


import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
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
    private lateinit var btnInfoScreen: ImageButton
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private var playlistManager = PlaylistManager()
    private val scope = CoroutineScope(Dispatchers.Main)
    private val viewManager = ViewManager()
    private val playlistDetailsList: MutableList<PlaylistDetails> = CopyOnWriteArrayList()
    private var muteCounter = 0

    companion object {
        private const val TAG = "PlayerActivity"
    }

    // initializes the activity and sets the layout
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate: Starting PlayerActivity initialization")
        
        try {
            super.onCreate(savedInstanceState)
            Log.d(TAG, "onCreate: Super onCreate completed successfully")
            
            setContentView(R.layout.activity_player)
            Log.d(TAG, "onCreate: Content view set successfully")

            // Set up global exception handler for this activity
            setupGlobalExceptionHandler()

            initializeActivity()
            Log.d(TAG, "onCreate: Activity initialization completed")

            // Register a BroadcastReceiver to update image resources
            setUpImageUpdateReceiver()
            Log.d(TAG, "onCreate: Image update receiver registered")

            val playlistRefresh = updatePlaylist()
            val muteStatus = checkMuteStatus()

            //refreshes the playlist every 30 seconds
            executor.scheduleAtFixedRate(playlistRefresh, 20, 30, TimeUnit.SECONDS)
            Log.d(TAG, "onCreate: Playlist refresh scheduled")
            
            // checks to see if stream has been muted every minute. if stream has been muted for...
            // 1 minute, it releases the player
            executor.scheduleAtFixedRate(muteStatus, 30, 30, TimeUnit.SECONDS)
            Log.d(TAG, "onCreate: Mute status check scheduled")

            // listens for button to be clicked
            btnPlayAudio.setOnClickListener {
                try {
                    Log.d(TAG, "Play audio button clicked")
                    toggleAudio()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in play audio button click handler", e)
                }
            }

            btnInfoScreen.setOnClickListener {
                try {
                    Log.d(TAG, "Info screen button clicked")
                    openInfoScreen()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in info screen button click handler", e)
                }
            }
            
            Log.i(TAG, "onCreate: PlayerActivity initialization completed successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL ERROR in onCreate: PlayerActivity failed to initialize", e)
            // Don't rethrow - let the app try to continue or fail gracefully
            Toast.makeText(this, "Failed to initialize app. Please restart.", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupGlobalExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            Log.e(TAG, "UNCAUGHT EXCEPTION in thread: ${thread.name}", exception)
            Log.e(TAG, "Exception details: ${exception.message}")
            Log.e(TAG, "Stack trace: ${exception.stackTraceToString()}")
            
            // Call the default handler to maintain normal crash behavior
            defaultHandler?.uncaughtException(thread, exception)
        }
        Log.d(TAG, "Global exception handler set up")
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy: Starting cleanup")
        
        try {
            super.onDestroy()
            Log.d(TAG, "onDestroy: Super onDestroy completed")
            
            stopService(Intent(this, AudioPlaybackService::class.java))
            Log.d(TAG, "onDestroy: AudioPlaybackService stopped")
            
            LocalBroadcastManager.getInstance(this).unregisterReceiver(imageUpdateReceiver)
            Log.d(TAG, "onDestroy: Image update receiver unregistered")
            
            executor.shutdown()
            Log.d(TAG, "onDestroy: Executor shutdown")
            
            Log.i(TAG, "onDestroy: Cleanup completed successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during onDestroy cleanup", e)
            // Continue with cleanup even if there are errors
        }
    }

    private fun initializeActivity() {
        Log.d(TAG, "initializeActivity: Starting activity initialization")
        
        try {
            // LoadingView used while the recyclerView is preparing
            loadingView = findViewById(R.id.loading_screen)
            Log.d(TAG, "initializeActivity: Loading view found")
            
            recyclerView = findViewById(R.id.recycler_view)
            Log.d(TAG, "initializeActivity: RecyclerView found")
            
            // initialization of properties
            streamImage = findViewById(R.id.streamImage)
            Log.d(TAG, "initializeActivity: Stream image found")
            
            btnPlayAudio = findViewById(R.id.toggleButton)
            Log.d(TAG, "initializeActivity: Play audio button found")
            
            btnInfoScreen = findViewById(R.id.btnInfoScreen)
            Log.d(TAG, "initializeActivity: Info screen button found")

            showLoadingView()
            Log.d(TAG, "initializeActivity: Loading view shown")

            startService(Intent(this, AudioPlaybackService::class.java))
            Log.d(TAG, "initializeActivity: AudioPlaybackService started")
            
            setInactiveStream()
            Log.d(TAG, "initializeActivity: Stream set to inactive state")

            //sets up initial playlist and then shows the playlist when ready
            scope.launch {
                try {
                    Log.d(TAG, "initializeActivity: Starting playlist setup coroutine")
                    setPlaylistDetailsList(playlistManager.fetchFullPlaylist())
                    Log.d(TAG, "initializeActivity: Playlist data fetched successfully")
                    
                    viewManager.setupRecyclerView(recyclerView, playlistDetailsList)
                    Log.d(TAG, "initializeActivity: RecyclerView setup completed")
                    
                    showContentView()
                    Log.d(TAG, "initializeActivity: Content view shown, initialization complete")
                } catch (e: Exception) {
                    Log.e(TAG, "Error in playlist setup coroutine", e)
                    runOnUiThread {
                        Toast.makeText(this@PlayerActivity, "Failed to load playlist. Check your internet connection.", Toast.LENGTH_LONG).show()
                        // Still show content view so user can potentially retry
                        showContentView()
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL ERROR in initializeActivity", e)
            Toast.makeText(this, "Failed to initialize app components", Toast.LENGTH_LONG).show()
            throw e // Re-throw to let onCreate handle it
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
        Log.d(TAG, "toggleAudio: Starting audio toggle")
        
        try {
            // handles extra, unnecessary clicks of button
            if (AudioPlaybackService.isPreparing) {
                Log.d(TAG, "toggleAudio: Audio service is preparing, ignoring click")
                return
            }
            
            Log.d(TAG, "toggleAudio: Current state - isPlaying: ${AudioPlaybackService.isPlaying}, isMuted: ${AudioPlaybackService.isMuted}, hasConnection: ${AudioPlaybackService.hasConnection}")
            
            // if audio stream is not running, we need to start the stream again
            if (!AudioPlaybackService.isPlaying) {
                Log.d(TAG, "toggleAudio: Audio stream is not playing")
                // case for stream is not playing but it appears to still be active, essentially resets things
                if (!AudioPlaybackService.isMuted){
                    Log.d(TAG, "toggleAudio: Resetting inactive stream")
                    setInactiveStream()
                }
                // audio stream is not playing, but we want to start the stream again
                else{
                    // starts the stream if there is internet connection
                    if (AudioPlaybackService.hasConnection) {
                        Log.d(TAG, "toggleAudio: Starting unmuted stream")
                        val audioServiceIntent = Intent(this, AudioPlaybackService::class.java)
                        audioServiceIntent.putExtra("action", "startUnmuted")
                        startService(audioServiceIntent)
                        setActiveStream()
                        Toast.makeText(applicationContext, "Loading WXYC stream", Toast.LENGTH_LONG)
                            .show()
                    }
                    // case where there is no internet
                    else {
                        Log.w(TAG, "toggleAudio: No internet connection available")
                        Toast.makeText(applicationContext, "No Internet Connection", Toast.LENGTH_LONG)
                            .show()
                    }
                }
            }
            // stream is running but just muted (paused) or unmuted (playing)
            else {
                Log.d(TAG, "toggleAudio: Audio stream is playing, toggling mute state")
                // audio is "playing", so we pause "mute" the stream
                if (!AudioPlaybackService.isMuted) {
                    Log.d(TAG, "toggleAudio: Muting audio stream")
                    val audioServiceIntent = Intent(this, AudioPlaybackService::class.java)
                    audioServiceIntent.putExtra("action", "mute")
                    startService(audioServiceIntent)
                    setInactiveStream()
                }
                // audio is "paused", so we play "unmute" the stream
                else {
                    Log.d(TAG, "toggleAudio: Unmuting audio stream")
                    val audioServiceIntent = Intent(this, AudioPlaybackService::class.java)
                    audioServiceIntent.putExtra("action", "unmute")
                    startService(audioServiceIntent)
                    setActiveStream()
                }
            }
            
            Log.d(TAG, "toggleAudio: Audio toggle completed successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL ERROR in toggleAudio", e)
            Toast.makeText(this, "Error controlling audio playback", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openInfoScreen() {
        val infoIntent = Intent(this,InfoScreen::class.java)
        startActivity(infoIntent)
    }

    // updates the playlist
    // this method is not completely perfect; if a new dj was to add multiple entries before a ...
    // .. refresh, a later entry after the updated 6 would be lost
    // however, the case described above is not common and would not effect user experience too much
    // this method is much more efficient than fully refreshing the playlist
    private fun updatePlaylist(): Runnable {
        return Runnable {
            scope.launch {
                try {
                    Log.d(TAG, "updatePlaylist: Starting playlist update check")
                    
                    // fetches last 7 updated playlist values
                    var updatedSubList = playlistManager.fetchLittlePlaylist()
                    Log.d(TAG, "updatePlaylist: Fetched ${updatedSubList?.size ?: 0} updated playlist items")
                    
                    // bool value to keep track of update type
                    var newEntry = false

                    // if there is no current playlist, skip this iteration of the updates
                    if (playlistDetailsList.size < UPDATE_UPPER_VALUE) {
                        Log.w(TAG, "updatePlaylist: Playlist not ready, size: ${playlistDetailsList.size}")
                        initializeActivity()
                        return@launch
                    }
                    if (updatedSubList.isNullOrEmpty()) {
                        Log.w(TAG, "updatePlaylist: Updated sublist is null or empty")
                        return@launch
                    }

                    // this section compares the first 7 entries of the current list and an updated list
                    // if there are differences the first 7 entries will be updated
                    if (updatedSubList.size != UPDATE_UPPER_VALUE) {
                        Log.d(TAG, "updatePlaylist: Trimming updated sublist from ${updatedSubList.size} to $UPDATE_UPPER_VALUE")
                        updatedSubList = updatedSubList.subList(0, UPDATE_UPPER_VALUE)
                    }

                    // fetches last 7 current playlist values
                    val currentSubList = playlistDetailsList.subList(0, UPDATE_UPPER_VALUE)

                    //  checks if the lists are the same
                    if (!compareLists(updatedSubList, currentSubList)) {
                        Log.d(TAG, "updatePlaylist: Lists are different, checking for updates")
                        // this determined if the two sublists are different.  now we need to check if
                        // an entry was added or there was an edit to the playlist (with no added entry)

                        // checks if the content in the lists are the same i.e. just an edit in order
                        newEntry = !compareListContent(updatedSubList, currentSubList)
                        Log.d(TAG, "updatePlaylist: New entry detected: $newEntry")
                        //COMPARISON PART OVER
                        fetchUpdatedPlaylistEntries(newEntry)
                    } else {
                        Log.d(TAG, "updatePlaylist: No update needed")
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error in updatePlaylist", e)
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
        streamImage.setImageResource(R.drawable.stream_active_short)
        btnPlayAudio.setImageResource(R.drawable.pause_button)
        AudioPlaybackService.isMuted = false
    }

    // sets inactive stream images
    private fun setInactiveStream() {
        streamImage.setImageResource(R.drawable.stream_inactive_short)
        btnPlayAudio.setImageResource(R.drawable.play_button)
        AudioPlaybackService.isMuted = true
    }
}

