package org.wxyc.wxycapp.ui

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import org.wxyc.wxycapp.data.Playcut
import data.PlaylistManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.wxyc.wxycapp.analytics.PostHogManager
import org.wxyc.wxycapp.data.PlaycutMetadata
import org.wxyc.wxycapp.data.metadata.PlaycutMetadataService
import playback.AudioPlaybackService
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject

data class PlayerUiState(
    val isLoading: Boolean = true,
    val isPlaying: Boolean = false, // Represents if audio is actually playing (not muted/stopped)
    val isMuted: Boolean = true, // We map 'stopped/paused' to 'muted' in legacy UI context slightly, but let's stick to isPlaying
    val playlist: List<Playcut> = emptyList(),
    val errorMessage: String? = null
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playlistManager: PlaylistManager,
    private val metadataService: PlaycutMetadataService
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()
    
    // Metadata state
    private val _metadata = MutableStateFlow<PlaycutMetadata?>(null)
    val metadata: StateFlow<PlaycutMetadata?> = _metadata.asStateFlow()
    
    private val _isLoadingMetadata = MutableStateFlow(false)
    val isLoadingMetadata: StateFlow<Boolean> = _isLoadingMetadata.asStateFlow()

    private val playlistDetailsList: MutableList<Playcut> = CopyOnWriteArrayList()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var playStartTime: Long = 0L // Track when playback started for duration calculation

    companion object {
        private const val TAG = "PlayerViewModel"
    }

    init {
        initializeMediaController()
    }

    private fun initializeMediaController() {
        val sessionToken = SessionToken(context, ComponentName(context, AudioPlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                controller = controllerFuture?.get()
                controller?.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _uiState.update { it.copy(isPlaying = isPlaying, isMuted = !isPlaying) }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        // Handle buffering etc if needed
                    }
                })
                // Sync initial state
                val isPlaying = controller?.isPlaying == true
                 _uiState.update { it.copy(isPlaying = isPlaying, isMuted = !isPlaying) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to MediaController", e)
            }
        }, MoreExecutors.directExecutor())
    }

    override fun onCleared() {
        super.onCleared()
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
    }

    fun togglePlayback() {
        controller?.let {
            if (it.isPlaying) {
                // Pause the player
                val duration = System.currentTimeMillis() - playStartTime
                PostHogManager.capturePause(
                    source = "PlayerViewModel.togglePlayback",
                    duration = duration,
                    reason = "User toggled playback"
                )
                it.pause()
            } else {
                // Play the player
                playStartTime = System.currentTimeMillis()
                PostHogManager.capturePlay(
                    source = "PlayerViewModel.togglePlayback",
                    reason = "User toggled playback"
                )
                it.play()
            }
        }
    }

    fun fetchPlaylist() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "fetchPlaylist: Starting playlist fetch")
                if (playlistDetailsList.isEmpty()) {
                  _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                }

                val playlist = playlistManager.fetchPlaylist()
                playlistDetailsList.clear()
                playlistDetailsList.addAll(playlist)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        playlist = playlistDetailsList.toList()
                    )
                }
                Log.d(TAG, "fetchPlaylist: Loaded ${playlist.size} items")
            } catch (e: Exception) {
                Log.e(TAG, "fetchPlaylist: Error loading playlist", e)
                
                // Track playlist fetch error
                PostHogManager.captureError(
                    error = e,
                    context = "PlayerViewModel.fetchPlaylist"
                )
                
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to load playlist. Check your internet connection."
                    )
                }
            }
        }
    }
    
    fun fetchMetadata(playcut: Playcut) {
        viewModelScope.launch {
            try {
                _isLoadingMetadata.value = true
                _metadata.value = null
                
                val fetchedMetadata = metadataService.fetchMetadata(playcut)
                
                _metadata.value = fetchedMetadata
                _isLoadingMetadata.value = false
            } catch (e: Exception) {
                Log.e(TAG, "fetchMetadata: Error fetching metadata", e)
                _isLoadingMetadata.value = false
                // Keep metadata as null on error
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
