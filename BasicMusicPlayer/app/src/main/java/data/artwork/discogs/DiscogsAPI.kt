package data.artwork.discogs

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface DiscogsAPI {
    @GET("database/search")
    suspend fun getImage(
        @Query("artist") artist: String,
        @Query("title") title: String,
        @Query("key") key: String,
        @Query("secret") secret: String
    ): Response<ResponseBody>

    @GET("database/search?type=artist")
    suspend fun getArtist(
        @Query("q") artist: String,
        @Query("key") key: String,
        @Query("secret") secret: String
    ): Response<ResponseBody>
}