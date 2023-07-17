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

            // initialize the JsonImporter class
            val jsonImporter = JsonImporter()
            // countdownlatch initialized to wait for callback to complete
            val latch = CountDownLatch(1) // this can be adapted to more advanced asynch programming if necessary

            val detailsCallback: (MutableList<PlaylistDetails>) -> Unit = { details ->
                playlist.addAll(details)
                // Process the song details here
                latch.countDown()
            }
            jsonImporter.fillFullPlaylist(detailsCallback)
            // Wait for the callback to complete
            latch.await()
            fetchPlaylistImages(playlist)
            return@withContext playlist
        }
    }


    // fetches the 5 most recent entries in the playlist without their image URLS
    suspend fun fetchLittlePlaylist(): MutableList<PlaylistDetails> {
        // Execute filling the playlist off of the main thread
        return withContext(Dispatchers.IO) {
            val playlist = mutableListOf<PlaylistDetails>()
            // initialize the JsonImporter class
            val jsonImporter = JsonImporter()
            // countdownlatch initialized to wait for callback to complete
            val latch = CountDownLatch(1) // this can be adapted to more advanced asynch programming if necessary

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



    // fetches the image urls for given playlist using asynch tasks
    private suspend fun fetchPlaylistImages(playlist: MutableList<PlaylistDetails>): Unit =
        coroutineScope {
            val playlistImager = PlaylistImager()
            val deferred = async {
                for (i in playlist.indices) {
                    //want to wait here for it to fetch
                    playlistImager.fetchImage(playlist[i])
                }
            }
            return@coroutineScope deferred
        }.await()
}