package data

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import data.artwork.PlaylistImager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch

// Manages the playlist creation
class PlaylistManager {


    suspend fun fetchFullPlaylist(): MutableList<PlaylistDetails> {

        // Execute filling the playlist off of the main thread
        return withContext(Dispatchers.IO) {
            val playlist = mutableListOf<PlaylistDetails>()

            // initialize the JsonImporter class
            val jsonImporter = JsonImporter()
            // countdownlatch initialized to wait for callback to complete
            val latch = CountDownLatch(1) // this can be adapted to more advanced asynch programming if necessary

            val callback: (MutableList<PlaylistDetails>) -> Unit = { details ->
                playlist.addAll(details)
                // Process the song details here
                latch.countDown()
            }

            jsonImporter.fillFullPlaylist(callback)

            // Wait for the callback to complete
            latch.await()

            return@withContext playlist
        }
    }

    suspend fun fetchLittlePlaylist(): MutableList<PlaylistDetails> {

        // Execute filling the playlist off of the main thread
        return withContext(Dispatchers.IO) {
            val playlist = mutableListOf<PlaylistDetails>()

            // initialize the JsonImporter class
            val jsonImporter = JsonImporter()
            // countdownlatch initialized to wait for callback to complete
            val latch = CountDownLatch(1) // this can be adapted to more advanced asynch programming if necessary

            val callback: (MutableList<PlaylistDetails>) -> Unit = { details ->
                playlist.addAll(details)
                // Process the song details here
                latch.countDown()
            }

            jsonImporter.fillLittlePlaylist(callback)

            // Wait for the callback to complete
            latch.await()

            return@withContext playlist
        }
    }



}