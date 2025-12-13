package data

import javax.inject.Inject
import data.artwork.PlaylistImager

// Manages the playlist creation
class PlaylistManager @Inject constructor(
    private val jsonImporter: JsonImporter,
    private val playlistImager: PlaylistImager
) {
    // fetches the full playlist
    suspend fun fetchFullPlaylist(): MutableList<PlaylistDetails> {
        val playlist = jsonImporter.getFullPlaylist().toMutableList()
        playlistImager.fetchPlaylistImageURLs(playlist)
        return playlist
    }

    // fetches the 6 most recent entries in the playlist without images
    suspend fun fetchLittlePlaylist(): MutableList<PlaylistDetails> {
        return jsonImporter.getLittlePlaylist().toMutableList()
    }

    // fetches the most recent entries in the playlist with their image URLS
    suspend fun fetchLittlePlaylistWithImages(): MutableList<PlaylistDetails> {
        val playlist = jsonImporter.getLittlePlaylist().toMutableList()
        playlistImager.fetchPlaylistImageURLs(playlist)
        return playlist
    }
}