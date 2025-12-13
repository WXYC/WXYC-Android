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
import data.Playcut
import data.PlaylistManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    private val playlistManager: PlaylistManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val playlistDetailsList: MutableList<Playcut> = CopyOnWriteArrayList()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

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
                it.pause()
            } else {
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
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to load playlist. Check your internet connection."
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
