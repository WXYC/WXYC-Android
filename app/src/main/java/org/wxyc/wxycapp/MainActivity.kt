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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.wxyc.wxycapp.ui.PlayerViewModel
import org.wxyc.wxycapp.ui.screens.InfoScreen
import org.wxyc.wxycapp.ui.screens.PlayerScreen
import org.wxyc.wxycapp.ui.theme.WXYCTheme
import playback.AudioPlaybackService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private var viewModel: PlayerViewModel? = null

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate: Starting MainActivity initialization")

        try {
            super.onCreate(savedInstanceState)
            
            // Starting service is good practice for MediaSessionService to ensure it runs
            startService(Intent(this, AudioPlaybackService::class.java))

            setupGlobalExceptionHandler()

            setContent {
                val vm: PlayerViewModel = viewModel()
                viewModel = vm
                val uiState by vm.uiState.collectAsState()
                
                val pagerState = rememberPagerState(pageCount = { 2 })
                val coroutineScope = rememberCoroutineScope()

                // Load initial playlist on first composition
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    vm.fetchPlaylist()
                }

                WXYCTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        HorizontalPager(state = pagerState) { page ->
                            when(page) {
                                0 -> PlayerScreen(
                                    uiState = uiState,
                                    onTogglePlayback = { vm.togglePlayback() }
                                )
                                1 -> InfoScreen()
                            }
                        }

                        // Pagination Dots
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            repeat(2) { iteration ->
                                val color = if (pagerState.currentPage == iteration) Color.White else Color.White.copy(alpha = 0.5f)
                                Box(
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .size(8.dp)
                                        .clickable {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(iteration)
                                            }
                                        }
                                )
                            }
                        }
                    }
                }
            }

            // Schedule playlist refresh
            val playlistRefresh = Runnable {
                viewModel?.fetchPlaylist()
            }
            executor.scheduleAtFixedRate(playlistRefresh, 20, 30, TimeUnit.SECONDS)
            Log.d(TAG, "onCreate: Playlist refresh scheduled")

            Log.i(TAG, "onCreate: MainActivity initialization completed successfully")

        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL ERROR in onCreate: MainActivity failed to initialize", e)
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
            executor.shutdown()
            Log.d(TAG, "onDestroy: Executor shutdown")
            Log.i(TAG, "onDestroy: Cleanup completed successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error during onDestroy cleanup", e)
        }
    }
}
