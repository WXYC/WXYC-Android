package data

import javax.inject.Inject
import org.wxyc.wxycapp.data.Playcut

class JsonImporter @Inject constructor(
    private val api: WxycApi
) {
    // Lets a fetch failure propagate to PlayerViewModel.fetchPlaylist's catch block,
    // which sets errorMessage and reports to analytics — swallowing it here made
    // that error path unreachable and a failed poll render as a silently empty list.
    suspend fun fetchPlaylist(): List<Playcut> {
        return api.getFlowsheet(35).entries.map { it.toDomain() }
    }
}
