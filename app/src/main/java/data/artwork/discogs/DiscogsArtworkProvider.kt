package data.artwork.discogs

import android.util.Log
import com.google.gson.Gson
import data.DiscogsResults
import org.wxyc.wxycapp.data.Playcut
import data.artwork.ArtworkProvider
import org.wxyc.wxycapp.BuildConfig
import javax.inject.Inject

class DiscogsArtworkProvider @Inject constructor(
    private val discogsService: DiscogsAPI
) : ArtworkProvider {
    override suspend fun fetchImage(playcut: Playcut): String? {
        try {
            val artist = playcut.artistName ?: return null
            var release = playcut.releaseTitle ?: return null
            if (release.equals("s/t", ignoreCase = true)) {
                release = artist
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
                    val imageUrl = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        val discogsJson = responseBody.string()
                        val searchResult = Gson().fromJson(discogsJson, DiscogsResults::class.java)
                        var foundUrl: String? = null
                        for (result in searchResult.results) {
                            if (result.coverImage?.endsWith(".gif") == false) {
                                foundUrl = result.coverImage
                                break
                            }
                        }
                        foundUrl
                    }

                    if (imageUrl != null) {
                        Log.i("DiscogsArtworkProvider", "Fetched image URL: $imageUrl")
                        return imageUrl
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
