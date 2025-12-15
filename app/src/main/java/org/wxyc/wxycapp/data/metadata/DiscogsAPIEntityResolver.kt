package org.wxyc.wxycapp.data.metadata

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.wxyc.wxycapp.data.api.DiscogsApiService
import org.wxyc.wxycapp.data.caching.CacheCoordinator
import org.wxyc.wxycapp.BuildConfig
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Implementation of DiscogsEntityResolver that fetches from Discogs API
 * and caches results for 30 days using CacheCoordinator.
 */
@Singleton
class DiscogsAPIEntityResolver @Inject constructor(
    private val discogsApi: DiscogsApiService,
    @ApplicationContext private val context: Context,
    @Named("MetadataCacheCoordinator") private val cacheCoordinator: CacheCoordinator
) : DiscogsEntityResolver {
    
    companion object {
        private const val TAG = "DiscogsEntityResolver"
        private const val CACHE_LIFESPAN_MS = 30L * 24 * 60 * 60 * 1000 // 30 days
        private const val DISCOGS_API_KEY = BuildConfig.DISCOGS_API_KEY
        private const val DISCOGS_SECRET_KEY = BuildConfig.DISCOGS_SECRET_KEY
    }
    
    override suspend fun resolveArtist(id: Int): String {
        val cacheKey = "artist_$id"
        
        // Check cache first
        cacheCoordinator.getValue<String>(cacheKey)?.let { return it }
        
        // Fetch from API
        val artist = discogsApi.getArtist(id, DISCOGS_API_KEY, DISCOGS_SECRET_KEY)
        
        // Cache the result
        cacheCoordinator.setValue(cacheKey, artist.name, CACHE_LIFESPAN_MS)
        
        return artist.name
    }
    
    override suspend fun resolveRelease(id: Int): String {
        val cacheKey = "release_$id"
        
        // Check cache first
        cacheCoordinator.getValue<String>(cacheKey)?.let { return it }
        
        // Fetch from API
        val release = discogsApi.getRelease(id, DISCOGS_API_KEY, DISCOGS_SECRET_KEY)
        
        // Cache the result
        cacheCoordinator.setValue(cacheKey, release.title, CACHE_LIFESPAN_MS)
        
        return release.title
    }
    
    override suspend fun resolveMaster(id: Int): String {
        val cacheKey = "master_$id"
        
        // Check cache first
        cacheCoordinator.getValue<String>(cacheKey)?.let { return it }
        
        // Fetch from API
        val master = discogsApi.getMaster(id, DISCOGS_API_KEY, DISCOGS_SECRET_KEY)
        
        // Cache the result
        cacheCoordinator.setValue(cacheKey, master.title, CACHE_LIFESPAN_MS)
        
        return master.title
    }
}
