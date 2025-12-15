package data

import javax.inject.Inject
import org.wxyc.wxycapp.data.Playcut
import data.artwork.PlaylistImager

// Manages the playlist creation
class PlaylistManager @Inject constructor(
    private val jsonImporter: JsonImporter,
    private val playlistImager: PlaylistImager
) {
    // fetches the full playlist
    suspend fun fetchPlaylist(): MutableList<Playcut> {
        val playlist = jsonImporter.fetchPlaylist().toMutableList()
        playlistImager.fetchPlaylistImageURLs(playlist)
        return playlist
    }
}