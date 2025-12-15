package org.wxyc.wxycapp.data.metadata

/**
 * Interface for resolving Discogs entity IDs to their names
 * Used by DiscogsMarkupParser to convert ID-based tags to readable text
 */
interface DiscogsEntityResolver {
    suspend fun resolveArtist(id: Int): String
    suspend fun resolveRelease(id: Int): String
    suspend fun resolveMaster(id: Int): String
}
