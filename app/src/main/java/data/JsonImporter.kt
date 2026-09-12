package data

import javax.inject.Inject
import org.wxyc.wxycapp.data.Playcut

class JsonImporter @Inject constructor(
    private val api: WxycApi
) {
    suspend fun fetchPlaylist(): List<Playcut> {
        return try {
            api.getFlowsheet(35).entries.map { it.toDomain() }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}