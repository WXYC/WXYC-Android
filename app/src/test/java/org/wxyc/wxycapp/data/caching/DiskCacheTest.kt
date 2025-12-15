package org.wxyc.wxycapp.data.caching

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.File

class DiskCacheTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var diskCache: DiskCache
    private lateinit var mockContext: Context
    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        mockContext = mock(Context::class.java)
        cacheDir = tempFolder.newFolder("cache")
        `when`(mockContext.cacheDir).thenReturn(cacheDir)

        diskCache = DiskCache(mockContext, "test_cache")
    }

    @Test
    fun `test set and get data`() {
        val key = "test_key"
        val data = "Hello, World!".toByteArray()
        val metadata = CacheMetadata(lifespan = 1000)

        diskCache.set(data, metadata, key)

        val retrievedData = diskCache.data(key)
        assertArrayEquals(data, retrievedData)

        val retrievedMetadata = diskCache.metadata(key)
        assertNotNull(retrievedMetadata)
        assertEquals(metadata.lifespan, retrievedMetadata?.lifespan)
    }

    @Test
    fun `test remove`() {
        val key = "test_key"
        val data = "Hello, World!".toByteArray()
        val metadata = CacheMetadata(lifespan = 1000)

        diskCache.set(data, metadata, key)
        diskCache.remove(key)

        assertNull(diskCache.data(key))
        assertNull(diskCache.metadata(key))
    }

    @Test
    fun `test overwrite`() {
        val key = "test_key"
        val data1 = "Data 1".toByteArray()
        val data2 = "Data 222".toByteArray()
        val metadata = CacheMetadata(lifespan = 1000)

        diskCache.set(data1, metadata, key)
        diskCache.set(data2, metadata, key)

        assertArrayEquals(data2, diskCache.data(key))
    }
}
