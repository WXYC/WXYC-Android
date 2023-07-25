package data

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import data.artwork.PlaylistImager
import kotlinx.coroutines.*
import java.util.concurrent.CountDownLatch

// Manages the playlist creation
class PlaylistManager {

    // fetches the full playlist
    suspend fun fetchFullPlaylist(): MutableList<PlaylistDetails> {

        // Execute filling the playlist off of the main thread
        return withContext(Dispatchers.IO) {
            val playlist = mutableListOf<PlaylistDetails>()
            val playlistImager = PlaylistImager()

            // initialize the JsonImporter class
            val jsonImporter = JsonImporter()
            // countdownlatch initialized to wait for callback to complete
            val latch =
                CountDownLatch(1) // this can be adapted to more advanced asynch programming if necessary

            val detailsCallback: (MutableList<PlaylistDetails>) -> Unit = { details ->
                playlist.addAll(details)
                // Process the song details here
                latch.countDown()
            }
            jsonImporter.fillFullPlaylist(detailsCallback)
            // Wait for the callback to complete
            latch.await()
            playlistImager.fetchPlaylistImages(playlist)
            return@withContext playlist
        }
    }


    // fetches the 5 most recent entries in the playlist with their image URLS
    suspend fun fetchLittlePlaylist(): MutableList<PlaylistDetails> {
        // Execute filling the playlist off of the main thread
        return withContext(Dispatchers.IO) {
            val playlist = mutableListOf<PlaylistDetails>()
            // initialize the JsonImporter class
            val jsonImporter = JsonImporter()
            // countdownlatch initialized to wait for callback to complete
            val latch =
                CountDownLatch(1) // this can be adapted to more advanced asynch programming if necessary

            val detailsCallback: (MutableList<PlaylistDetails>) -> Unit = { details ->
                playlist.addAll(details)
                // Process the song details here
                latch.countDown()
            }
            jsonImporter.fillLittlePlaylist(detailsCallback)
            // Wait for the callback to complete
            latch.await()
            return@withContext playlist
        }
    }

    // fetches the most recent entries in the playlist with their image URLS
    suspend fun fetchLittlePlaylistWithImages(): MutableList<PlaylistDetails> {
        // Execute filling the playlist off of the main thread
        return withContext(Dispatchers.IO) {
            val playlist = mutableListOf<PlaylistDetails>()
            val playlistImager = PlaylistImager()

            // initialize the JsonImporter class
            val jsonImporter = JsonImporter()
            // countdownlatch initialized to wait for callback to complete
            val latch =
                CountDownLatch(1) // this can be adapted to more advanced asynch programming if necessary

            val detailsCallback: (MutableList<PlaylistDetails>) -> Unit = { details ->
                playlist.addAll(details)
                // Process the song details here
                latch.countDown()
            }
            jsonImporter.fillLittlePlaylist(detailsCallback)
            // Wait for the callback to complete
            latch.await()
            playlistImager.fetchPlaylistImages(playlist)
            return@withContext playlist
        }
    }



}