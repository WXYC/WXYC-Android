package org.wxyc.wxycapp.data.metadata

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.wxyc.wxycapp.BuildConfig
import org.wxyc.wxycapp.data.Playcut
import org.wxyc.wxycapp.data.PlaycutMetadata
import org.wxyc.wxycapp.data.api.DiscogsApiService
import org.wxyc.wxycapp.data.api.ITunesApiService
import org.wxyc.wxycapp.data.api.SpotifyApiService
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Service for fetching extended playcut metadata from various sources
 * Integrates with Discogs, Spotify, iTunes, and generates search URLs for other services
 */
@Singleton
class PlaycutMetadataService @Inject constructor(
    private val discogsApi: DiscogsApiService,
    private val spotifyAuthApi: SpotifyApiService,
    @Named("SpotifySearch") private val spotifySearchApi: SpotifyApiService,
    private val itunesApi: ITunesApiService,
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREF_NAME_METADATA_CACHE, Context.MODE_PRIVATE)
    }
    
    private val gson = Gson()
    
    // Spotify token management (synchronized for thread safety)
    @Volatile private var spotifyToken: String? = null
    @Volatile private var spotifyTokenExpiration: Long = 0
    
    companion object {
        private const val TAG = "PlaycutMetadataService"
        private const val PREF_NAME_METADATA_CACHE = "playcut_metadata_cache"
        private const val CACHE_LIFESPAN_MS = 7L * 24 * 60 * 60 * 1000 // 7 days
        private const val DISCOGS_API_KEY = BuildConfig.DISCOGS_API_KEY
        private const val DISCOGS_SECRET_KEY = BuildConfig.DISCOGS_SECRET_KEY
        private const val SPOTIFY_CLIENT_ID = BuildConfig.SPOTIFY_CLIENT_ID
        private const val SPOTIFY_CLIENT_SECRET = BuildConfig.SPOTIFY_CLIENT_SECRET
    }
    
    /**
     * Fetches all available metadata for a playcut
     */
    suspend fun fetchMetadata(playcut: Playcut): PlaycutMetadata {
        val cacheKey = "metadata_${playcut.id}"
        
        // Check cache first
        getCachedMetadata(cacheKey)?.let { return it }
        
        // Fetch from all sources concurrently
        val metadata = coroutineScope {
            val discogsDeferred = async { fetchDiscogsMetadata(playcut) }
            val spotifyDeferred = async { fetchSpotifyURL(playcut) }
            val appleMusicDeferred = async { fetchAppleMusicURL(playcut) }
            val youtubeMusicDeferred = async { makeYouTubeMusicSearchURL(playcut) }
            val bandcampDeferred = async { makeBandcampSearchURL(playcut) }
            val soundcloudDeferred = async { makeSoundCloudSearchURL(playcut) }
            
            val discogs = discogsDeferred.await()
            
            PlaycutMetadata(
                label = discogs.label ?: playcut.labelName,
                releaseYear = discogs.releaseYear,
                discogsURL = discogs.discogsURL,
                artistBio = discogs.artistBio,
                wikipediaURL = discogs.wikipediaURL,
                spotifyURL = spotifyDeferred.await(),
                appleMusicURL = appleMusicDeferred.await(),
                youtubeMusicURL = youtubeMusicDeferred.await(),
                bandcampURL = bandcampDeferred.await(),
                soundcloudURL = soundcloudDeferred.await()
            )
        }
        
        // Cache result
        cacheMetadata(cacheKey, metadata)
        
        return metadata
    }
    
    // MARK: - Discogs Integration
    
    private data class DiscogsMetadata(
        val label: String?,
        val releaseYear: Int?,
        val discogsURL: String?,
        val artistBio: String?,
        val wikipediaURL: String?
    )
    
    private suspend fun fetchDiscogsMetadata(playcut: Playcut): DiscogsMetadata {
        return try {
            // Search for the release
            var releaseTitle = playcut.releaseTitle
            if (releaseTitle?.lowercase() == "s/t") {
                releaseTitle = playcut.artistName
            }
            
            val searchTerms = buildListOf(
                playcut.artistName,
                releaseTitle
            ).joinToString(" ")
            
            val searchResults = discogsApi.search(
                query = searchTerms,
                apiKey = DISCOGS_API_KEY,
                apiSecret = DISCOGS_SECRET_KEY
            )
            
            // Find first valid result (skip spacer.gif placeholders)
            val result = searchResults.results.firstOrNull { 
                !it.coverImage.contains("spacer.gif")
            }
            
            if (result == null) {
                return DiscogsMetadata(null, null, null, null, null)
            }
            
            // Try to fetch artist info for bio and Wikipedia link
            var artistBio: String? = null
            var wikipediaURL: String? = null
            
            when (result.type) {
                "release" -> {
                    result.resourceUrl?.let { url ->
                        try {
                            val release = discogsApi.getRelease(
                                releaseId = result.id,
                                apiKey = DISCOGS_API_KEY,
                                apiSecret = DISCOGS_SECRET_KEY
                            )
                            release.primaryArtistId?.let { artistId ->
                                val artist = discogsApi.getArtist(
                                    artistId = artistId,
                                    apiKey = DISCOGS_API_KEY,
                                    apiSecret = DISCOGS_SECRET_KEY
                                )
                                artistBio = artist.profile
                                wikipediaURL = artist.wikipediaURL
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to fetch artist details from release", e)
                        }
                    }
                }
                "master" -> {
                    result.resourceUrl?.let {
                        try {
                            val master = discogsApi.getMaster(
                                masterId = result.id,
                                apiKey = DISCOGS_API_KEY,
                                apiSecret = DISCOGS_SECRET_KEY
                            )
                            master.primaryArtistId?.let { artistId ->
                                val artist = discogsApi.getArtist(
                                    artistId = artistId,
                                    apiKey = DISCOGS_API_KEY,
                                    apiSecret = DISCOGS_SECRET_KEY
                                )
                                artistBio = artist.profile
                                wikipediaURL = artist.wikipediaURL
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to fetch artist details from master", e)
                        }
                    }
                }
                "artist" -> {
                    // Direct artist result
                    try {
                        val artist = discogsApi.getArtist(
                            artistId = result.id,
                            apiKey = DISCOGS_API_KEY,
                            apiSecret = DISCOGS_SECRET_KEY
                        )
                        
                        artistBio = artist.profile
                        wikipediaURL = artist.wikipediaURL
                        
                        Log.d(TAG, "Extracted wikipediaURL: $wikipediaURL")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to fetch artist details", e)
                    }
                }
            }
            
            val metadata = DiscogsMetadata(
                label = result.primaryLabel,
                releaseYear = result.releaseYear,
                discogsURL = result.discogsWebURL,
                artistBio = artistBio,
                wikipediaURL = wikipediaURL
            )
            
            metadata
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch Discogs metadata for ${playcut.artistName} - ${playcut.songTitle}", e)
            DiscogsMetadata(null, null, null, null, null)
        }
    }
    
    // MARK: - Spotify Integration
    
    private suspend fun fetchSpotifyURL(playcut: Playcut): String? {
        return try {
            val token = getSpotifyToken()
            
            val query = buildString {
                append("track:${playcut.songTitle} artist:${playcut.artistName}")
                playcut.releaseTitle?.let { append(" album:$it") }
            }
            
            val response = spotifySearchApi.search(
                authorization = "Bearer $token",
                query = query
            )
            
            response.tracks?.items?.firstOrNull()?.external_urls?.spotify
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch Spotify URL", e)
            null
        }
    }
    
    private suspend fun getSpotifyToken(): String {
        // Return cached token if valid
        if (spotifyToken != null && System.currentTimeMillis() < spotifyTokenExpiration - 60000) {
            return spotifyToken!!
        }
        
        // Fetch new token
        val credentials = "$SPOTIFY_CLIENT_ID:$SPOTIFY_CLIENT_SECRET"
        val base64Credentials = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
        
        val response = spotifyAuthApi.getAccessToken(
            authorization = "Basic $base64Credentials"
        )
        
        spotifyToken = response.access_token
        spotifyTokenExpiration = System.currentTimeMillis() + (response.expires_in * 1000L)
        
        return response.access_token
    }
    
    // MARK: - Apple Music Integration
    
    private suspend fun fetchAppleMusicURL(playcut: Playcut): String? {
        return try {
            val query = "${playcut.artistName} ${playcut.songTitle}"
            val response = itunesApi.search(term = query)
            response.results.firstOrNull()?.trackViewUrl
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch Apple Music URL", e)
            null
        }
    }
    
    // MARK: - Other Services (Search URLs)
    
    private fun makeYouTubeMusicSearchURL(playcut: Playcut): String {
        val query = "${playcut.artistName} ${playcut.songTitle}"
            .replace(" ", "+")
        return "https://music.youtube.com/search?q=$query"
    }
    
    private fun makeBandcampSearchURL(playcut: Playcut): String {
        val query = "${playcut.artistName} ${playcut.songTitle}"
            .replace(" ", "%20")
        return "https://bandcamp.com/search?q=$query"
    }
    
    private fun makeSoundCloudSearchURL(playcut: Playcut): String {
        val query = "${playcut.artistName} ${playcut.songTitle}"
            .replace(" ", "%20")
        return "https://soundcloud.com/search?q=$query"
    }
    
    // MARK: - Caching
    
    private fun getCachedMetadata(key: String): PlaycutMetadata? {
        val json = prefs.getString(key, null) ?: return null
        val entry = gson.fromJson(json, CacheEntry::class.java)
        
        // Check if expired
        if (System.currentTimeMillis() - entry.timestamp > CACHE_LIFESPAN_MS) {
            prefs.edit().remove(key).apply()
            return null
        }
        
        return entry.metadata
    }
    
    private fun cacheMetadata(key: String, metadata: PlaycutMetadata) {
        val entry = CacheEntry(metadata, System.currentTimeMillis())
        prefs.edit().putString(key, gson.toJson(entry)).apply()
    }
    
    private data class CacheEntry(
        val metadata: PlaycutMetadata,
        val timestamp: Long
    )
    
    private fun <T> buildListOf(vararg elements: T?): List<T> {
        return elements.filterNotNull()
    }
}
