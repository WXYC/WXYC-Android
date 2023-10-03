package data

import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.result.Result
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import data.artwork.PlaylistImager
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

// class imports the json playlist information
class JsonImporter {
    private val gson = Gson()

    //json feed url
    val jsonFullURL = "http://wxyc.info/playlists/recentEntries?n=35"
    val jsonLittleURL = "http://wxyc.info/playlists/recentEntries?n=12"

    // fills the full playlist
    fun fillFullPlaylist(callback: (MutableList<PlaylistDetails>) -> Unit) {
        val playlistDetails = mutableListOf<PlaylistDetails>()
        // http get request with lambda expression for handling the result
        jsonFullURL.httpGet().responseString { _, _, result ->
            when (result) {
                is Result.Success -> {
                    val jsonString = result.value
                    // parse json string into an array
                    val jsonArray = JsonParser.parseString(jsonString).asJsonArray
                    // sorts through the entries in the json array
                    for (jsonElement in jsonArray) {
                        // defines val entryType to sort between talksets and songs
                        val playCut = gson.fromJson(jsonElement, PlaylistDetails::class.java)
                        playCut.playcut.let { playCutDetails ->
                            playlistDetails.add(playCut)
                        }
                    }
                    callback(playlistDetails) // invoke the callback with the populated list
                }
                is Result.Failure -> {
                    val error = result.error
                    // Handle the error
                    callback(playlistDetails) // invoke the callback with the empty list or handle the error case separately
                }
            }
        }
    }

    // fills a playlist with only the 5 most recent entries
    fun fillLittlePlaylist(callback: (MutableList<PlaylistDetails>) -> Unit) {
        val playlistDetails = mutableListOf<PlaylistDetails>()
        // http get request with lambda expression for handling the result
        jsonLittleURL.httpGet().responseString { _, _, result ->
            when (result) {
                is Result.Success -> {
                    println("jsonimporter Success")
                    val jsonString = result.value
                    // parse json string into an array
                    val jsonArray = JsonParser.parseString(jsonString).asJsonArray
                    // sorts through the entries in the json array
                    for (jsonElement in jsonArray) {
                        // defines val entryType to sort between talksets and songs
                        val playCut = gson.fromJson(jsonElement, PlaylistDetails::class.java)
                        playCut.playcut.let { playCutDetails ->
                            playlistDetails.add(playCut)
                        }
                    }
                    callback(playlistDetails) // invoke the callback with the populated list
                }
                is Result.Failure -> {
                    println("jsonimporter FAILED")
                    val error = result.error
                    // Handle the error
                    println("Error: $error")
                    callback(playlistDetails) // invoke the callback with the empty list or handle the error case separately
                }
            }
        }
    }
}