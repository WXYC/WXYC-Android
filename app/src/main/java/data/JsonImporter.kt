package data

import data.dto.OnAir
import javax.inject.Inject
import org.wxyc.wxycapp.data.Playcut

/** One poll of the flowsheet: the rows plus who is on air. */
data class Flowsheet(val playcuts: List<Playcut>, val onAir: OnAir)

class JsonImporter @Inject constructor(
    private val api: WxycApi
) {
    // Lets a fetch failure propagate to PlayerViewModel.fetchPlaylist's catch block,
    // which sets errorMessage and reports to analytics — swallowing it here made
    // that error path unreachable and a failed poll render as a silently empty list.
    suspend fun fetchPlaylist(): Flowsheet {
        val response = api.getFlowsheet(35)
        return Flowsheet(response.toPlaycuts(), response.onAir)
    }
}
