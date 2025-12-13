package data.artwork.discogs

import android.util.Log
import com.google.gson.Gson
import data.DiscogsResults
import data.PlaylistDetails
import data.artwork.ArtworkProvider
import org.wxyc.wxycapp.BuildConfig
import javax.inject.Inject

class DiscogsArtworkProvider @Inject constructor(
    private val discogsService: DiscogsAPI
) : ArtworkProvider {
    override suspend fun fetchImage(playcut: PlaylistDetails): String? {
        try {
            val artist = playcut.playcut.artistName
            var release = playcut.playcut.releaseTitle
            if (release.equals("s/t", ignoreCase = true)) {
                release = playcut.playcut.artistName
            }

            val response = discogsService.getImage(
                artist,
                release,
                BuildConfig.DISCOGS_API_KEY,
                BuildConfig.DISCOGS_SECRET_KEY
            )

            if (response.isSuccessful) {
                val responseBody = response.body()
                if (responseBody != null) {
                    val discogsJson = responseBody.string()
                    val searchResult = Gson().fromJson(discogsJson, DiscogsResults::class.java)
                    for (result in searchResult.results) {
                        if (result.coverImage?.endsWith(".gif") == false) {
                            Log.i("DiscogsArtworkProvider", "Fetched image URL: ${result.coverImage}")
                            return result.coverImage
                        }
                    }
                    Log.w("DiscogsArtworkProvider", "No suitable image found in Discogs results")
                } else {
                    Log.w("DiscogsArtworkProvider", "Discogs response body was null")
                }
            } else {
                Log.e("DiscogsArtworkProvider", "Error fetching image: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e("DiscogsArtworkProvider", "Exception fetching image", e)
        }
        return null
    }
}
