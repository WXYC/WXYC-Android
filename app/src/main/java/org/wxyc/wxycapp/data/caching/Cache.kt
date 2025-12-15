package org.wxyc.wxycapp.data.caching

/**
 * Protocol for a cache backend (e.g., Disk, Memory).
 */
interface Cache {
    /**
     * Read metadata only (e.g., from sidecar file)
     */
    fun metadata(key: String): CacheMetadata?

    /**
     * Read data (file contents)
     */
    fun data(key: String): ByteArray?

    /**
     * Write both data and metadata
     */
    fun set(data: ByteArray?, metadata: CacheMetadata, key: String)

    /**
     * Delete entry
     */
    fun remove(key: String)

    /**
     * For pruning - only reads metadata, never file contents
     */
    fun allMetadata(): List<Pair<String, CacheMetadata>>
}
