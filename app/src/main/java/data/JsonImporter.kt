package data

import javax.inject.Inject

class JsonImporter @Inject constructor(
    private val api: WxycApi
) {
    suspend fun fetchPlaylist(): List<PlaylistDetails> {
        return try {
            api.getRecentEntries(35)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}