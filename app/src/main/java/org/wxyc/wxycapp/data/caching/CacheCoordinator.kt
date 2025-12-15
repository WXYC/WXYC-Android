package org.wxyc.wxycapp.data.caching

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import java.lang.reflect.Type

/**
 * Coordinator that manages a [Cache] instance.
 * Handles serialization (Gson), expiration checks, and cleanup.
 */
class CacheCoordinator @Inject constructor(
    private val cache: Cache
) {
    private val gson = Gson()

    /**
     * Retrieve a typed value from the cache.
     * Returns null if missing or expired.
     */
    suspend fun <T> getValue(key: String, type: Type): T? = withContext(Dispatchers.IO) {
        val metadata = cache.metadata(key) ?: return@withContext null

        if (metadata.isExpired) {
            Log.i(TAG, "Cache expired for $key")
            cache.remove(key)
            return@withContext null
        }

        val bytes = cache.data(key) ?: return@withContext null
        try {
            val json = String(bytes, Charsets.UTF_8)
            val value = gson.fromJson<T>(json, type)
            Log.v(TAG, "Cache hit for $key")
            value
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deserialize $key", e)
            null
        }
    }
    
    /**
     * Reified helper for getValue
     */
    suspend inline fun <reified T> getValue(key: String): T? {
        return getValue(key, object : TypeToken<T>() {}.type)
    }

    /**
     * Store a typed value in the cache.
     */
    suspend fun <T> setValue(key: String, value: T, lifespanMs: Long) = withContext(Dispatchers.IO) {
        /*
         * Note: We serialize everything to JSON strings for simplicity.
         * For images/binary, we should use [setData] instead.
         */
        val json = gson.toJson(value)
        val data = json.toByteArray(Charsets.UTF_8)
        val metadata = CacheMetadata(lifespan = lifespanMs)
        cache.set(data, metadata, key)
        Log.v(TAG, "Cache set for $key")
    }

    /**
     * Retrieve raw data.
     */
    suspend fun getData(key: String): ByteArray? = withContext(Dispatchers.IO) {
        val metadata = cache.metadata(key) ?: return@withContext null

        if (metadata.isExpired) {
            cache.remove(key)
            return@withContext null
        }
        
        return@withContext cache.data(key)
    }

    /**
     * Store raw data.
     */
    suspend fun setData(key: String, data: ByteArray, lifespanMs: Long) = withContext(Dispatchers.IO) {
        val metadata = CacheMetadata(lifespan = lifespanMs)
        cache.set(data, metadata, key)
    }

    /**
     * Purge all expired entries.
     */
    suspend fun purgeExpiredEntries() = withContext(Dispatchers.IO) {
        Log.i(TAG, "Purging expired entries...")
        val all = cache.allMetadata()
        var count = 0
        for ((key, meta) in all) {
            if (meta.isExpired) {
                cache.remove(key)
                count++
            }
        }
        Log.i(TAG, "Purged $count expired entries")
    }
    
    companion object {
        private const val TAG = "CacheCoordinator"
    }
}
