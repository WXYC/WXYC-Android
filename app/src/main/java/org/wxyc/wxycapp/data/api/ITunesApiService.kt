package org.wxyc.wxycapp.data.api

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit service for iTunes Search API
 * Documentation: https://developer.apple.com/library/archive/documentation/AudioVideo/Conceptual/iTuneSearchAPI/
 */
interface ITunesApiService {
    
    /**
     * Search for tracks in iTunes/Apple Music
     */
    @GET("/search")
    suspend fun search(
        @Query("term") term: String,
        @Query("entity") entity: String = "song",
        @Query("media") media: String = "music",
        @Query("limit") limit: Int = 1
    ): ITunesSearchResponse
}

/**
 * iTunes search response
 */
data class ITunesSearchResponse(
    val resultCount: Int,
    val results: List<ITunesTrack>
)

data class ITunesTrack(
    val trackId: Long,
    val trackName: String,
    val artistName: String,
    val trackViewUrl: String?
)
