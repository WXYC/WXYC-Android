package data.artwork.itunes

import android.util.Log
import com.google.gson.Gson
import data.PlaylistDetails
import data.artwork.ArtworkProvider
import data.artwork.itunes.ITunesResults
import javax.inject.Inject

class ItunesArtworkProvider @Inject constructor(
    private val iTunesService: ITunesAPI
) : ArtworkProvider {
    override suspend fun fetchImage(playcut: PlaylistDetails): String? {
        try {
            val artist = playcut.playcut.artistName
            var release = playcut.playcut.releaseTitle
            if (release.equals("s/t", ignoreCase = true)) {
                release = playcut.playcut.artistName
            }
            val itunesSearch = "$artist $release"
            val response = iTunesService.getImage(itunesSearch)
            if (response.isSuccessful) {
                val responseBody = response.body()
                if (responseBody != null) {
                    val itunesJson = responseBody.string()
                    val searchResult = Gson().fromJson(itunesJson, ITunesResults::class.java)
                    val imageUrl = searchResult.results.firstOrNull()?.artworkUrl100
                    if (!imageUrl.isNullOrEmpty()) {
                        return imageUrl.replace("100x100bb", "1200x1200")
                    }
                }
            } else {
                Log.e("ItunesArtworkProvider", "Error fetching image: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e("ItunesArtworkProvider", "Exception fetching image", e)
        }
        return null
    }
}
