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

private const val UPDATE_UPPER_VALUE = 6
private const val UPDATE_LOWER_VALUE = 0
private const val NEW_ENTRY_UPDATE_UPPER_VALUE = 5

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

    fun loadInitialPlaylist() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "loadInitialPlaylist: Starting playlist fetch")
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }

                val playlist = playlistManager.fetchFullPlaylist()
                playlistDetailsList.clear()
                playlistDetailsList.addAll(playlist)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        playlist = playlistDetailsList.toList()
                    )
                }
                Log.d(TAG, "loadInitialPlaylist: Loaded ${playlist.size} items")
            } catch (e: Exception) {
                Log.e(TAG, "loadInitialPlaylist: Error loading playlist", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to load playlist. Check your internet connection."
                    )
                }
            }
        }
    }

    fun updatePlaylist() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "updatePlaylist: Starting playlist update check")

                val updatedSubList = playlistManager.fetchLittlePlaylist()?.take(UPDATE_UPPER_VALUE)
                    ?: return@launch

                if (playlistDetailsList.size < UPDATE_UPPER_VALUE) {
                    Log.w(TAG, "updatePlaylist: Playlist not ready")
                    loadInitialPlaylist()
                    return@launch
                }

                val currentSubList = playlistDetailsList.take(UPDATE_UPPER_VALUE)

                if (!compareLists(updatedSubList, currentSubList)) {
                    Log.d(TAG, "updatePlaylist: Lists are different, fetching updates")
                    val newEntry = !compareListContent(updatedSubList, currentSubList)
                    fetchUpdatedPlaylistEntries(newEntry)
                }
            } catch (e: Exception) {
                Log.e(TAG, "updatePlaylist: Error updating playlist", e)
            }
        }
    }

    private suspend fun fetchUpdatedPlaylistEntries(newEntry: Boolean) {
        val updatedSublistWithImages = playlistManager.fetchLittlePlaylistWithImages()

        if (!newEntry) {
            // Just an edit in the playlist order
            for (i in UPDATE_LOWER_VALUE until UPDATE_UPPER_VALUE) {
                if (i < playlistDetailsList.size) {
                    playlistDetailsList.removeAt(UPDATE_LOWER_VALUE)
                }
            }
            playlistDetailsList.addAll(
                0,
                updatedSublistWithImages.subList(UPDATE_LOWER_VALUE, UPDATE_UPPER_VALUE)
            )
        } else {
            // New entry added
            for (i in UPDATE_LOWER_VALUE until NEW_ENTRY_UPDATE_UPPER_VALUE) {
                if (playlistDetailsList.isNotEmpty()) {
                    playlistDetailsList.removeAt(UPDATE_LOWER_VALUE)
                }
            }
            playlistDetailsList.addAll(
                0,
                updatedSublistWithImages.subList(UPDATE_LOWER_VALUE, UPDATE_UPPER_VALUE)
            )
        }

        _uiState.update { it.copy(playlist = playlistDetailsList.toList()) }
    }

    private fun compareLists(listOne: List<PlaylistDetails>, listTwo: List<PlaylistDetails>): Boolean {
        if (listOne.size != listTwo.size) return false
        return listOne.indices.all { listOne[it].id == listTwo[it].id }
    }

    private fun compareListContent(listOne: List<PlaylistDetails>, listTwo: List<PlaylistDetails>): Boolean {
        val setOne = listOne.map { it.id }.toSet()
        val setTwo = listTwo.map { it.id }.toSet()
        return setOne == setTwo
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
