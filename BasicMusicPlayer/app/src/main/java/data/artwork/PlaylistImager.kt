package data.artwork

import com.bumptech.glide.Glide
import com.example.basicmusicplayer.PlayerActivity
import com.example.basicmusicplayer.R
import com.google.gson.Gson
import data.DiscogsResults
import data.PlaylistDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


class PlaylistImager {

//Lets split this up into 2 different tasks. First lets find the image url and put it into the playcut details
    // next we can create a function that accesses the url and puts the image into the view
    //this aligns more with the current structure of things
    suspend fun fetchImage(playcut: PlaylistDetails) {
       // println("TRIED TO FETCH THE IMAGE")
            try {
             //   println("FETCH IMAGE CHECKPOINT 1")

                val artist = playcut.playcut.artistName
                val release = playcut.playcut.releaseTitle


/*
                val artist = "The Beatles"
                val release = "Revolver"


 */
                


                val response = DiscogsArtFetcher.discogsService.getImage(artist, release, "tYvsaskeJxOQbWoZSSkh", "vZuPZFFDerXIPrBfSNnNyDhXjpIUiyXi")
                if (response.isSuccessful) {
                    //println("FETCH IMAGE CHECKPOINT 2")
                    val responseBody = response.body()
                    if (responseBody != null) {
                        val discogsJson = responseBody.string()
                        val searchResult = Gson().fromJson(discogsJson, DiscogsResults::class.java)
                        /*
                        println("SEARCH RESULTS:")
                        println(searchResult)

                         */
                        val imageUrl = searchResult.results.firstOrNull()?.thumb

                        if (imageUrl != null) {
                            playcut.playcut.imageURL = imageUrl
                        }
                        /*
                        println(" ")
                        println(artist)
                        println(release)
                        println(imageUrl)
                        println(" ")
                         */

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
   // return null
    }
}