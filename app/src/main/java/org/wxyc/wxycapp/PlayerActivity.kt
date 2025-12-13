package org.wxyc.wxycapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.wxyc.wxycapp.ui.PlayerViewModel
import org.wxyc.wxycapp.ui.screens.PlayerScreen
import org.wxyc.wxycapp.ui.theme.WXYCTheme
import playback.AudioPlaybackService
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {
    private lateinit var imageUpdateReceiver: BroadcastReceiver
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private var muteCounter = 0
    private var viewModel: PlayerViewModel? = null

    companion object {
        private const val TAG = "PlayerActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate: Starting PlayerActivity initialization")

        try {
            super.onCreate(savedInstanceState)
            Log.d(TAG, "onCreate: Super onCreate completed successfully")

            setupGlobalExceptionHandler()

            // Start the audio service
            startService(Intent(this, AudioPlaybackService::class.java))
            Log.d(TAG, "onCreate: AudioPlaybackService started")

            // Set up image update receiver
            setUpImageUpdateReceiver()
            Log.d(TAG, "onCreate: Image update receiver registered")

            setContent {
                val vm: PlayerViewModel = viewModel()
                viewModel = vm
                val uiState by vm.uiState.collectAsState()

                // Load initial playlist on first composition
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    vm.loadInitialPlaylist()
                }

                WXYCTheme {
                    PlayerScreen(
                        uiState = uiState,
                        onTogglePlayback = { toggleAudio() },
                        onInfoClick = { openInfoScreen() }
                    )
                }
            }

            // Schedule playlist refresh
            val playlistRefresh = Runnable {
                viewModel?.updatePlaylist()
            }
            executor.scheduleAtFixedRate(playlistRefresh, 20, 30, TimeUnit.SECONDS)
            Log.d(TAG, "onCreate: Playlist refresh scheduled")

            // Schedule mute status check
            val muteStatus = checkMuteStatus()
            executor.scheduleAtFixedRate(muteStatus, 30, 30, TimeUnit.SECONDS)
            Log.d(TAG, "onCreate: Mute status check scheduled")

            Log.i(TAG, "onCreate: PlayerActivity initialization completed successfully")

        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL ERROR in onCreate: PlayerActivity failed to initialize", e)
            Toast.makeText(this, "Failed to initialize app. Please restart.", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupGlobalExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            Log.e(TAG, "UNCAUGHT EXCEPTION in thread: ${thread.name}", exception)
            Log.e(TAG, "Exception details: ${exception.message}")
            Log.e(TAG, "Stack trace: ${exception.stackTraceToString()}")
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
        }
    }

    private fun toggleAudio() {
        Log.d(TAG, "toggleAudio: Starting audio toggle")

        try {
            if (AudioPlaybackService.isPreparing) {
                Log.d(TAG, "toggleAudio: Audio service is preparing, ignoring click")
                return
            }

            Log.d(TAG, "toggleAudio: Current state - isPlaying: ${AudioPlaybackService.isPlaying}, isMuted: ${AudioPlaybackService.isMuted}, hasConnection: ${AudioPlaybackService.hasConnection}")

            if (!AudioPlaybackService.isPlaying) {
                Log.d(TAG, "toggleAudio: Audio stream is not playing")
                if (!AudioPlaybackService.isMuted) {
                    Log.d(TAG, "toggleAudio: Resetting inactive stream")
                    setInactiveStream()
                } else {
                    if (AudioPlaybackService.hasConnection) {
                        Log.d(TAG, "toggleAudio: Starting unmuted stream")
                        val audioServiceIntent = Intent(this, AudioPlaybackService::class.java)
                        audioServiceIntent.putExtra("action", "startUnmuted")
                        startService(audioServiceIntent)
                        setActiveStream()
                        Toast.makeText(applicationContext, "Loading WXYC stream", Toast.LENGTH_LONG).show()
                    } else {
                        Log.w(TAG, "toggleAudio: No internet connection available")
                        Toast.makeText(applicationContext, "No Internet Connection", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                Log.d(TAG, "toggleAudio: Audio stream is playing, toggling mute state")
                if (!AudioPlaybackService.isMuted) {
                    Log.d(TAG, "toggleAudio: Muting audio stream")
                    val audioServiceIntent = Intent(this, AudioPlaybackService::class.java)
                    audioServiceIntent.putExtra("action", "mute")
                    startService(audioServiceIntent)
                    setInactiveStream()
                } else {
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
        val infoIntent = Intent(this, InfoScreen::class.java)
        startActivity(infoIntent)
    }

    private fun checkMuteStatus(): Runnable {
        return Runnable {
            if (AudioPlaybackService.isPlaying) {
                if (AudioPlaybackService.isMuted) {
                    muteCounter += 1
                }
                if (muteCounter == 2) {
                    muteCounter = 0
                    stopService(Intent(this, AudioPlaybackService::class.java))
                }
            }
        }
    }

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

    private fun setActiveStream() {
        viewModel?.setStreamActive()
        AudioPlaybackService.isMuted = false
    }

    private fun setInactiveStream() {
        viewModel?.setStreamInactive()
        AudioPlaybackService.isMuted = true
    }
}
