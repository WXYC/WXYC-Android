package data

import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.result.Result
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser

class JsonImporter {

    private val gson = Gson()

    // data class for playcut
    data class PlayCutDetails(
        val rotation: String,
        val request: String,
        val songTitle: String,
        val labelName: String,
        val artistName: String,
        val releaseTitle: String
    )

    // data class for json entries
    data class JsonEntry(
        val id: Int,
        val entryType: String,
        val playcut: PlayCutDetails,
        val hour: Long,
        val chronOrderID: Int
    )


    //json feed url
    val jsonURL = "http://wxyc.info/playlists/recentEntries?n=8"


    fun fillPlaylist(callback: (MutableList<Pair<String, String>>) -> Unit) {
        val songDetails = mutableListOf<Pair<String, String>>()


        // http get request with lambda expression for handling the result
        jsonURL.httpGet().responseString { _, _, result ->
            when (result) {
                is Result.Success -> {
                    val jsonString = result.value

                    // parse json string into an array
                    val jsonArray = JsonParser.parseString(jsonString).asJsonArray

                    // sorts through the entries in the json array
                    for (jsonElement in jsonArray) {
                        // defines val entryType to sort between talksets and songs
                        val entryType = jsonElement.asJsonObject.get("entryType").asString
                        // only add "playcuts" to the songDetails
                        if (entryType == "playcut") {
                            val playCut = gson.fromJson(jsonElement, JsonEntry::class.java)
                            playCut.playcut.let { playCutDetails ->
                                val songTitle = playCutDetails.songTitle
                                val artistName = playCutDetails.artistName
                                val songDetail = Pair(songTitle, artistName)
                                songDetails.add(songDetail)
                            }
                        }
                        if (entryType == "talkset") {
                            val talksetHour = getTalksetHour(jsonElement.asJsonObject)
                            songDetails.add(Pair("talkset", talksetHour))
                        }
                        if (entryType == "breakpoint") {
                            val breakpointHour = getBreakpointHour(jsonElement.asJsonObject)
                            songDetails.add(Pair("breakpoint", breakpointHour))
                        }
                    }

                    callback(songDetails) // invoke the callback with the populated list
                }
                is Result.Failure -> {
                    println("json FAILED")
                    val error = result.error
                    // Handle the error
                    println("Error: $error")

                    callback(songDetails) // invoke the callback with the empty list or handle the error case separately
                }
            }
        }
    }

    private fun getTalksetHour(jsonObject: JsonObject): String {
        val hour = jsonObject.get("hour").asString
        return hour
    }

    private fun getBreakpointHour(jsonObject: JsonObject): String {
        val hour = jsonObject.get("hour").asString
        return hour
    }

}