package data.artwork.itunes

import android.util.Log
import com.google.gson.Gson
import data.Playcut
import data.artwork.ArtworkProvider
import data.artwork.itunes.ITunesResults
import javax.inject.Inject

class ItunesArtworkProvider @Inject constructor(
    private val iTunesService: ITunesAPI
) : ArtworkProvider {
    override suspend fun fetchImage(playcut: Playcut): String? {
        try {
            val artist = playcut.artistName ?: return null
            var release = playcut.releaseTitle ?: return null
            if (release.equals("s/t", ignoreCase = true)) {
                release = artist
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
