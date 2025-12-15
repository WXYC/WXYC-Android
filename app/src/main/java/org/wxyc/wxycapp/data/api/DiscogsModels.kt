package org.wxyc.wxycapp.data.api

import com.google.gson.annotations.SerializedName

/**
 * Discogs API response models
 * Based on Discogs API v2 specification
 */
object DiscogsModels {
    
    /**
     * Search results wrapper
     */
    data class SearchResults(
        val results: List<SearchResult>
    )
    
    /**
     * Individual search result
     */
    data class SearchResult(
        @SerializedName("cover_image")
        val coverImage: String,
        
        @SerializedName("master_id")
        val masterId: Int?,
        
        val id: Int,
        val type: String,
        val label: List<String>?,
        val year: String?,
        val uri: String?,
        
        @SerializedName("resource_url")
        val resourceUrl: String?
    ) {
        /**
         * Constructs the full Discogs web URL from the uri field
         */
        val discogsWebURL: String?
            get() = uri?.let { "https://www.discogs.com$it" }
        
        /**
         * Parsed release year as Int
         */
        val releaseYear: Int?
            get() = year?.toIntOrNull()
        
        /**
         * First label name if available
         */
        val primaryLabel: String?
            get() = label?.firstOrNull()
    }
    
    /**
     * Artist details
     */
    data class Artist(
        val id: Int,
        val name: String,
        val profile: String?,
        val urls: List<String>?,
        val images: List<ArtistImage>?
    ) {
        /**
         * Finds Wikipedia URL from the urls array
         */
        val wikipediaURL: String?
            get() = urls?.firstOrNull { url ->
                url.lowercase().contains("wikipedia.org") ||
                url.lowercase().contains("en.wikipedia")
            }
    }
    
    data class ArtistImage(
        val uri: String,
        val type: String
    )
    
    /**
     * Release details
     */
    data class Release(
        val id: Int,
        val title: String,
        val year: Int?,
        val labels: List<Label>?,
        val artists: List<ReleaseArtist>?,
        val uri: String?
    ) {
        data class Label(
            val name: String,
            val id: Int
        )
        
        data class ReleaseArtist(
            val id: Int,
            val name: String
        )
        
        val primaryLabel: String?
            get() = labels?.firstOrNull()?.name
        
        val primaryArtistId: Int?
            get() = artists?.firstOrNull()?.id
        
        val discogsWebURL: String?
            get() = uri?.let { "https://www.discogs.com$it" }
    }
    
    /**
     * Master release details
     */
    data class Master(
        val id: Int,
        val title: String,
        val year: Int?,
        val uri: String?,
        val artists: List<ReleaseArtist>?
    ) {
        data class ReleaseArtist(
            val id: Int,
            val name: String
        )
        
        val primaryArtistId: Int?
            get() = artists?.firstOrNull()?.id
        
        val discogsWebURL: String?
            get() = uri?.let { "https://www.discogs.com$it" }
    }
}
