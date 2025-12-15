package data.artwork.lastfm

import android.util.Log
import com.google.gson.Gson
import org.wxyc.wxycapp.data.Playcut
import data.artwork.ArtworkProvider
import org.wxyc.wxycapp.BuildConfig
import javax.inject.Inject

class LastFmArtworkProvider @Inject constructor(
    private val lastFmService: LastFmAPI
) : ArtworkProvider {
    private val LASTFM_METHOD = "album.getinfo"

    override suspend fun fetchImage(playcut: Playcut): String? {
        try {
            val artist = playcut.artistName ?: return null
            var release = playcut.releaseTitle ?: return null
            if (release.equals("s/t", ignoreCase = true)) {
                release = artist
            }
            val response = lastFmService.getAlbumInfo(
                LASTFM_METHOD, BuildConfig.LASTFM_API_KEY, artist,
                release, "json"
            )
            if (response.isSuccessful) {
                val responseBody = response.body()
                if (responseBody != null) {
                    val imageUrl = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        val lastfmJson = responseBody.string()
                        val cleanedJson = lastfmJson.replace("\\/", "/")
                        val searchResult = Gson().fromJson(cleanedJson, LastFmResults::class.java)
                        searchResult.album.image.getOrNull(4)?.text
                    }

                    if (!imageUrl.isNullOrEmpty()) {
                        return imageUrl
                    }
                }
            } else {
                Log.e("LastFmArtworkProvider", "Error fetching image: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e("LastFmArtworkProvider", "Exception fetching image", e)
        }
        return null
    }
}
