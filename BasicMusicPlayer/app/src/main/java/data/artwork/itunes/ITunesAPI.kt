package data.artwork.itunes

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface ITunesAPI {
    @GET("search")
    suspend fun getImage(
        @Query("term") term: String
    ): Response<ResponseBody>

}