package data.artwork.discogs

import android.util.Log
import com.google.gson.Gson
import data.PlaylistDetails
import data.artwork.ArtworkProvider
import org.wxyc.wxycapp.BuildConfig
import javax.inject.Inject

class DiscogsArtistArtworkProvider @Inject constructor(
    private val discogsService: DiscogsAPI
) : ArtworkProvider {
    private val VARIOUS_ARTISTS = "v/a"
    private val VARIOUS_ARTISTS_FULL = "various artists"

    override suspend fun fetchImage(playcut: PlaylistDetails): String? {
        try {
            var artist = playcut.playcut.artistName
            if (artist.equals(VARIOUS_ARTISTS, ignoreCase = true) || artist.equals(
                    VARIOUS_ARTISTS_FULL, ignoreCase = true
                )
            ) {
                artist = "Various Artists"
            }
            val response = discogsService.getArtist(
                artist,
                BuildConfig.DISCOGS_API_KEY,
                BuildConfig.DISCOGS_SECRET_KEY
            )
            if (response.isSuccessful) {
                val responseBody = response.body()
                if (responseBody != null) {
                    val discogsJson = responseBody.string()
                    val searchResult =
                        Gson().fromJson(discogsJson, DiscogsArtistsResults::class.java)
                    val imageUrl = searchResult.results.firstOrNull()?.cover_image
                    if (imageUrl?.endsWith(".gif") == false) {
                        Log.i("DiscogsArtistProvider", "Fetched image URL: $imageUrl")
                        return imageUrl
                    }
                }
            } else {
                Log.e("DiscogsArtistProvider", "Error fetching artist image: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e("DiscogsArtistProvider", "Exception fetching artist image", e)
        }
        return null
    }
}
