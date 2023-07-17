package data.artwork

import com.bumptech.glide.Glide
import com.example.basicmusicplayer.PlayerActivity
import com.example.basicmusicplayer.R
import com.google.gson.Gson
import data.DiscogsResults
import data.PlaylistDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// fetches playlist image data. NEED TO CONCEAL THE KEY
class PlaylistImager {

    suspend fun fetchImage(playcut: PlaylistDetails) {
        try {

            val artist = playcut.playcut.artistName
            val release = playcut.playcut.releaseTitle


            val response = DiscogsArtFetcher.discogsService.getImage(
                artist,
                release,
                "tYvsaskeJxOQbWoZSSkh",
                "vZuPZFFDerXIPrBfSNnNyDhXjpIUiyXi"
            )
            if (response.isSuccessful) {
                //println("FETCH IMAGE CHECKPOINT 2")
                val responseBody = response.body()
                if (responseBody != null) {
                    val discogsJson = responseBody.string()
                    val searchResult = Gson().fromJson(discogsJson, DiscogsResults::class.java)

                    val imageUrl = searchResult.results.firstOrNull()?.thumb

                    if (imageUrl != null) {
                        playcut.playcut.imageURL = imageUrl
                    } else {
                        println("discogs null url")
                    }
                } else {
                    // Handle empty response body
                    println("empty image data")
                }
            } else {
                // Handle the error response
                println("discogs art fetcher failed")
            }
        } catch (e: Exception) {
            // Handle network or other exceptions
        }
    }
}