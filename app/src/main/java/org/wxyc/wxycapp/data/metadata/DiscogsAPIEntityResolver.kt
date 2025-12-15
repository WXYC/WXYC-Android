package org.wxyc.wxycapp.data.metadata

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import org.wxyc.wxycapp.data.api.DiscogsApiService
import org.wxyc.wxycapp.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of DiscogsEntityResolver that fetches from Discogs API
 * and caches results for 30 days
 */
@Singleton
class DiscogsAPIEntityResolver @Inject constructor(
    private val discogsApi: DiscogsApiService,
    @ApplicationContext private val context: Context
) : DiscogsEntityResolver {
    
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREF_NAME_ENTITY_CACHE, Context.MODE_PRIVATE)
    }
    
    private val gson = Gson()
    
    companion object {
        private const val TAG = "DiscogsEntityResolver"
        private const val PREF_NAME_ENTITY_CACHE = "discogs_entity_cache"
        private const val CACHE_LIFESPAN_MS = 30L * 24 * 60 * 60 * 1000 // 30 days
        private const val DISCOGS_API_KEY = BuildConfig.DISCOGS_API_KEY
        private const val DISCOGS_SECRET_KEY = BuildConfig.DISCOGS_SECRET_KEY
    }
    
    override suspend fun resolveArtist(id: Int): String {
        val cacheKey = "artist_$id"
        
        // Check cache first
        getCached(cacheKey)?.let { return it }
        
        // Fetch from API
        val artist = discogsApi.getArtist(id, DISCOGS_API_KEY, DISCOGS_SECRET_KEY)
        
        // Cache the result
        cache(cacheKey, artist.name)
        
        return artist.name
    }
    
    override suspend fun resolveRelease(id: Int): String {
        val cacheKey = "release_$id"
        
        // Check cache first
        getCached(cacheKey)?.let { return it }
        
        // Fetch from API
        val release = discogsApi.getRelease(id, DISCOGS_API_KEY, DISCOGS_SECRET_KEY)
        
        // Cache the result
        cache(cacheKey, release.title)
        
        return release.title
    }
    
    override suspend fun resolveMaster(id: Int): String {
        val cacheKey = "master_$id"
        
        // Check cache first
        getCached(cacheKey)?.let { return it }
        
        // Fetch from API
        val master = discogsApi.getMaster(id, DISCOGS_API_KEY, DISCOGS_SECRET_KEY)
        
        // Cache the result
        cache(cacheKey, master.title)
        
        return master.title
    }
    
    private fun getCached(key: String): String? {
        val json = prefs.getString(key, null) ?: return null
        val entry = gson.fromJson(json, CacheEntry::class.java)
        
        // Check if expired
        if (System.currentTimeMillis() - entry.timestamp > CACHE_LIFESPAN_MS) {
            prefs.edit().remove(key).apply()
            return null
        }
        
        return entry.value
    }
    
    private fun cache(key: String, value: String) {
        val entry = CacheEntry(value, System.currentTimeMillis())
        prefs.edit().putString(key, gson.toJson(entry)).apply()
    }
    
    private data class CacheEntry(
        val value: String,
        val timestamp: Long
    )
}
