package org.wxyc.wxycapp.data.api

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Retrofit service for Spotify Web API
 * Documentation: https://developer.spotify.com/documentation/web-api/
 */
interface SpotifyApiService {
    
    /**
     * Get access token using Client Credentials flow
     * https://developer.spotify.com/documentation/general/guides/authorization-guide/#client-credentials-flow
     */
    @POST("/api/token")
    @FormUrlEncoded
    suspend fun getAccessToken(
        @Header("Authorization") authorization: String,
        @Field("grant_type") grantType: String = "client_credentials"
    ): SpotifyTokenResponse
    
    /**
     * Search for tracks
     * https://developer.spotify.com/documentation/web-api/reference/search
     */
    @GET("/v1/search")
    suspend fun search(
        @Header("Authorization") authorization: String,
        @Query("q") query: String,
        @Query("type") type: String = "track",
        @Query("limit") limit: Int = 1
    ): SpotifySearchResponse
}

/**
 * Spotify token response
 */
data class SpotifyTokenResponse(
    val access_token: String,
    val token_type: String,
    val expires_in: Int
)

/**
 * Spotify search response
 */
data class SpotifySearchResponse(
    val tracks: SpotifyTracks?
)

data class SpotifyTracks(
    val items: List<SpotifyTrack>
)

data class SpotifyTrack(
    val id: String,
    val name: String,
    val external_urls: SpotifyExternalUrls?
)

data class SpotifyExternalUrls(
    val spotify: String?
)
