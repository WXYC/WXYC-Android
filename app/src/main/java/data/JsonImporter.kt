package data

import javax.inject.Inject
import org.wxyc.wxycapp.data.Playcut

class JsonImporter @Inject constructor(
    private val api: WxycApi
) {
    suspend fun fetchPlaylist(): List<Playcut> {
        return try {
            api.getRecentEntries(35).map { it.toDomain() }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}