package data.artwork.lastfm


import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Query
import okhttp3.ResponseBody


interface LastFmAPI {
    @GET("/2.0/?")
    suspend fun getAlbumInfo(
        @Query("method") method: String,
        @Query("api_key") apiKey: String,
        @Query("artist") artist: String,
        @Query("album") album: String,
        @Query("format") format: String
    ): Response<ResponseBody>
}


