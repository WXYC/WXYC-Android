package data

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import data.artwork.PlaylistImager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch

class PlaylistManager {
    // lists to store song details
    private var playlist = mutableListOf<PlaylistDetails>()
    //var updatedPlaylistDetailsList = mutableListOf<PlaylistDetails>()


    //maybe this shouldnt take in the view and we keep that fully separate
    suspend fun fetchPlaylist(): MutableList<PlaylistDetails>{
        // Execute filling the playlist off of the main thread
        return withContext(Dispatchers.IO){
            // initialize the JsonImporter class
            val jsonImporter = JsonImporter()
            // countdownlatch initialized to wait for callback to complete
            val latch = CountDownLatch(1) // this can be adapted to more advanced asynch programming if necessary


            val callback: (MutableList<PlaylistDetails>) -> Unit = { details ->
                playlist.addAll(details)
                // Process the song details here
                latch.countDown()
            }

                jsonImporter.fillPlaylist(callback)


            // Wait for the callback to complete
            latch.await()


            return@withContext playlist

        }
        /*
        // initialize the JsonImporter class
        val jsonImporter = JsonImporter()
        // countdownlatch initialized to wait for callback to complete
        val latch = CountDownLatch(1) // this can be adapted to more advanced asynch programming if necessary


        val callback: (MutableList<PlaylistDetails>) -> Unit = { details ->
            playlistDetailsList.addAll(details)
            // Process the song details here
            latch.countDown()
        }

        // Execute filling the playlist off of the main thread
        withContext(Dispatchers.IO){
            jsonImporter.fillPlaylist(callback)
        }

        // Wait for the callback to complete
        latch.await()


        return playlistDetailsList

         */

    }

    // ** this code may need to be adjusted

    /*
    suspend fun updatePlaylist(unrefreshedPlaylist: MutableList<PlaylistDetails>): MutableList<PlaylistDetails>{
        //println("update playlist 1")
        val jsonImporter = JsonImporter()
        // countdown latch initialized
        val latch = CountDownLatch(1) // this can be adapted to more advanced asynch programming if necessary

        val noUpdate: Pair<MutableList<PlaylistDetails>, Boolean> = Pair(unrefreshedPlaylist, false)
        // list to store song details

       /* if (playlistDetailsList.isNotEmpty()) {
            playlistDetailsList.clear()
        }
        */


        val callback: (MutableList<PlaylistDetails>) -> Unit = { songDetails ->
            val updatedPlaylist = mutableListOf<PlaylistDetails>()
            updatedPlaylist.addAll((songDetails))
            playlistDetailsList.clear()
            playlistDetailsList.addAll(updatedPlaylist)
            // Process the song details here
            latch.countDown()
        }
        withContext(Dispatchers.IO){
            jsonImporter.fillPlaylist(callback)
        }
        // Wait for the callback to complete
        latch.await()


        println("playcut indi")
        //these arent different when they should be thats the problem
        println(playlistDetailsList[0].playcut)
        println(unrefreshedPlaylist[0].playcut)


        // parses through the playlist, looking for changes.
        // this is an imperfect approach. if dj edits the order in the playlist it won't catch it
        if (playlistDetailsList[0] != unrefreshedPlaylist[0]){
            val newSong = playlistDetailsList[0]
            unrefreshedPlaylist.add(0, newSong)
            println("YEAH YEAH")
            return Pair(unrefreshedPlaylist, true)

        }

        println("NAH NAH")
        return noUpdate
    }

     */

}