package data

import retrofit2.http.GET
import retrofit2.http.Query

interface WxycApi {
    @GET("playlists/recentEntries")
    suspend fun getRecentEntries(@Query("n") limit: Int): List<data.dto.PlaylistResponseDto>
}
