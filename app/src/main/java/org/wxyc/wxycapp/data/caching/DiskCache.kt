package org.wxyc.wxycapp.data.caching

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/**
 * File-system based cache implementation.
 * Stores binary data in `key` and metadata in `key.meta`.
 */
class DiskCache(
    private val context: Context,
    private val subDirectory: String
) : Cache {

    private val gson = Gson()
    private val cacheDir: File by lazy {
        File(context.cacheDir, subDirectory).also {
            if (!it.exists()) {
                it.mkdirs()
            }
        }
    }

    private fun getFile(key: String): File = File(cacheDir, key)
    private fun getMetaFile(key: String): File = File(cacheDir, "$key.meta")

    override fun metadata(key: String): CacheMetadata? {
        val metaFile = getMetaFile(key)
        if (!metaFile.exists()) return null
        return try {
            val json = metaFile.readText()
            gson.fromJson(json, CacheMetadata::class.java)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read metadata for $key", e)
            null
        }
    }

    override fun data(key: String): ByteArray? {
        val file = getFile(key)
        if (!file.exists()) return null
        return try {
            file.readBytes()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read data for $key", e)
            null
        }
    }

    override fun set(data: ByteArray?, metadata: CacheMetadata, key: String) {
        val file = getFile(key)
        val metaFile = getMetaFile(key)

        if (data == null) {
            remove(key)
            return
        }

        try {
            file.writeBytes(data)
            metaFile.writeText(gson.toJson(metadata))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write cache entry for $key", e)
            // Cleanup on partial failure
            file.delete()
            metaFile.delete()
        }
    }

    override fun remove(key: String) {
        getFile(key).delete()
        getMetaFile(key).delete()
    }

    override fun allMetadata(): List<Pair<String, CacheMetadata>> {
        val files = cacheDir.listFiles { _, name -> name.endsWith(".meta") } ?: return emptyList()
        return files.mapNotNull { metaFile ->
            val key = metaFile.name.removeSuffix(".meta")
            // Re-use existing method to parse safely
            metadata(key)?.let { meta ->
                key to meta
            }
        }
    }

    companion object {
        private const val TAG = "DiskCache"
    }
}
