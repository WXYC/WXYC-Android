package data

import data.dto.FlowsheetResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

interface WxycApi {
    @GET("flowsheet")
    suspend fun getFlowsheet(@Query("limit") limit: Int): FlowsheetResponseDto
}
