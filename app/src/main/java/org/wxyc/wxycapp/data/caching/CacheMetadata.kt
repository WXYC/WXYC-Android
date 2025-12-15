package org.wxyc.wxycapp.data.caching

/**
 * Metadata for a cached item.
 */
data class CacheMetadata(
    val timestamp: Long = System.currentTimeMillis(),
    val lifespan: Long
) {
    val isExpired: Boolean
        get() = (System.currentTimeMillis() - timestamp) > lifespan
}
