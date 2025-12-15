package org.wxyc.wxycapp.data.api

import org.wxyc.wxycapp.data.api.DiscogsModels.SearchResults
import org.wxyc.wxycapp.data.api.DiscogsModels.Artist
import org.wxyc.wxycapp.data.api.DiscogsModels.Release
import org.wxyc.wxycapp.data.api.DiscogsModels.Master
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit service for Discogs API v2
 * Documentation: https://www.discogs.com/developers
 */
interface DiscogsApiService {
    
    /**
     * Search the Discogs database
     */
    @GET("/database/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("key") apiKey: String,
        @Query("secret") apiSecret: String,
        @Query("type") type: String? = null
    ): SearchResults
    
    /**
     * Get artist details by ID
     */
    @GET("/artists/{id}")
    suspend fun getArtist(
        @Path("id") artistId: Int,
        @Query("key") apiKey: String,
        @Query("secret") apiSecret: String
    ): Artist
    
    /**
     * Get release details by ID
     */
    @GET("/releases/{id}")
    suspend fun getRelease(
        @Path("id") releaseId: Int,
        @Query("key") apiKey: String,
        @Query("secret") apiSecret: String
    ): Release
    
    /**
     * Get master release details by ID
     */
    @GET("/masters/{id}")
    suspend fun getMaster(
        @Path("id") masterId: Int,
        @Query("key") apiKey: String,
        @Query("secret") apiSecret: String
    ): Master
}
