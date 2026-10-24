package org.wxyc.wxycapp.ui

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import org.wxyc.wxycapp.data.Playcut
import org.wxyc.wxycapp.data.bio.ArtistBioClient
import org.wxyc.wxycapp.data.bio.BioFetchPolicy
import org.wxyc.wxycapp.data.bio.BioToken
import org.wxyc.wxycapp.data.bio.BioTokensResult
import org.wxyc.wxycapp.data.bio.RecentMisses
import data.JsonImporter
import data.dto.NowMetadata
import data.dto.nowLead
import org.wxyc.wxycapp.R
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.wxyc.wxycapp.analytics.PostHogManager
import playback.AudioPlaybackService
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject

data class PlayerUiState(
    val isLoading: Boolean = true,
    val isPlaying: Boolean = false, // Represents if audio is actually playing (not muted/stopped)
    val isMuted: Boolean = true, // We map 'stopped/paused' to 'muted' in legacy UI context slightly, but let's stick to isPlaying
    val playlist: List<Playcut> = emptyList(),
    val errorMessage: String? = null,
    /** Server-parsed artist bios by playcut id, loaded when a detail sheet opens. */
    val bioTokens: Map<Int, List<BioToken>> = emptyMap()
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val jsonImporter: JsonImporter,
    private val artistBioClient: ArtistBioClient
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val playlistDetailsList: MutableList<Playcut> = CopyOnWriteArrayList()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    companion object {
        private const val TAG = "PlayerViewModel"
        private const val BIO_MISS_TTL_SECONDS = 300L
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
                publishNowMetadata(nowMetadata)
                // Sync initial state
                val isPlaying = controller?.isPlaying == true
                 _uiState.update { it.copy(isPlaying = isPlaying, isMuted = !isPlaying) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to MediaController", e)
            }
        }, MoreExecutors.directExecutor())
    }

    private var nowMetadata: NowMetadata? = null
    private var publishedMetadata: NowMetadata? = null

    /**
     * Hands the lead to the media session, which owns the notification and lock screen.
     * Replacing the item's metadata in place does not restart the stream, and an
     * unchanged lead is not sent again on every poll. The controller connects
     * asynchronously, so the latest lead is kept and sent once it is up.
     */
    private fun publishNowMetadata(lead: NowMetadata?) {
        nowMetadata = lead
        val controller = controller ?: return
        if (lead == publishedMetadata || controller.mediaItemCount == 0) return
        publishedMetadata = lead
        val metadata = MediaMetadata.Builder().apply {
            if (lead == null) return@apply
            setTitle(lead.title)
            setArtist(lead.artist)
            if (lead.artworkUrl != null) setArtworkUri(Uri.parse(lead.artworkUrl))
            else setArtworkData(stationArtwork, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        }.build()
        controller.replaceMediaItem(
            0,
            MediaItem.Builder().setUri(AudioPlaybackService.STREAM_URL).setMediaMetadata(metadata).build()
        )
    }

    private val stationArtwork: ByteArray by lazy {
        val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.wxyc_mainlogo_lowqual)
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    override fun onCleared() {
        super.onCleared()
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
    }

    /**
     * Toggles playback.
     *
     * Emits no analytics itself. `play` / `pause` are captured in
     * `AudioPlaybackService`, which sees commands from the media notification, headset
     * buttons and the reconnect path as well as this one — capturing here counted only
     * the in-app control and left `source` with a single value (WXYC-Android#48). The
     * command reaches the service through the session, where it is attributed to
     * `CommandOrigin.IN_APP`.
     */
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

                val flowsheet = jsonImporter.fetchPlaylist()
                val playlist = flowsheet.playcuts
                publishNowMetadata(nowLead(playlist, flowsheet.onAir))
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
    
    /** Playcuts whose bio lookup just missed; not retried for a few minutes. */
    private val bioMisses = RecentMisses(ttlSeconds = BIO_MISS_TTL_SECONDS)

    /** Playcuts whose bio is final for this session; never looked up again. */
    private val completeBios = java.util.Collections.synchronizedSet(mutableSetOf<Int>())

    /**
     * Loads a server-assisted rendering of [playcut]'s bio into [PlayerUiState.bioTokens]
     * (see [ArtistBioClient.renderedBio]) when [BioFetchPolicy.shouldFetch] allows it. A
     * complete result is kept for good. A lookup that finds nothing, fails, or resolves only
     * part of the bio's ids is tried again after [BIO_MISS_TTL_SECONDS]; a partial result is
     * shown meanwhile, and otherwise the sheet keeps the locally parsed bio.
     */
    fun loadBioTokens(playcut: Playcut) {
        val artist = playcut.artistName ?: return
        val bio = playcut.metadata?.artistBio ?: return
        if (!BioFetchPolicy.shouldFetch(playcut) || playcut.id in completeBios || bioMisses.isRecent(playcut.id)) return
        viewModelScope.launch {
            val result = artistBioClient.renderedBio(artist, playcut.releaseTitle, bio)
            if (result is BioTokensResult.Found) {
                _uiState.update { it.copy(bioTokens = it.bioTokens + (playcut.id to result.tokens)) }
            }
            if (result is BioTokensResult.Found && result.complete) completeBios += playcut.id
            else bioMisses.record(playcut.id)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
