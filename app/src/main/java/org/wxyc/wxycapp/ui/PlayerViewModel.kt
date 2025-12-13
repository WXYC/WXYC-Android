package org.wxyc.wxycapp.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import data.PlaylistDetails
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
    val isPlaying: Boolean = false,
    val isMuted: Boolean = true,
    val playlist: List<PlaylistDetails> = emptyList(),
    val errorMessage: String? = null
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playlistManager: PlaylistManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val playlistDetailsList: MutableList<PlaylistDetails> = CopyOnWriteArrayList()

    companion object {
        private const val TAG = "PlayerViewModel"
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
    fun setStreamActive() {
        _uiState.update { it.copy(isPlaying = true, isMuted = false) }
    }

    fun setStreamInactive() {
        _uiState.update { it.copy(isMuted = true) }
    }

    fun setStreamStopped() {
        _uiState.update { it.copy(isPlaying = false, isMuted = true) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
