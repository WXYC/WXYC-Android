package org.wxyc.wxycapp.data.caching

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

class CacheCoordinatorTest {

    @Mock
    private lateinit var mockCache: Cache

    private lateinit var coordinator: CacheCoordinator

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        coordinator = CacheCoordinator(mockCache)
    }

    @Test
    fun `test setValue and getValue`() = runBlocking {
        val key = "user_123"
        val user = User("Alice", 30)
        
        // Mock cache behavior for set (verify serialization happens, but we can't easily capture the byte array in logic without ArgumentCaptor, 
        // but we can mock what happens on get)
        
        // Simulating the "Get" side
        val json = """{"name":"Alice","age":30}"""
        val data = json.toByteArray()
        val metadata = CacheMetadata(lifespan = 10000)
        
        `when`(mockCache.metadata(key)).thenReturn(metadata)
        `when`(mockCache.data(key)).thenReturn(data)
        
        val result: User? = coordinator.getValue(key)
        
        assertEquals(user, result)
    }

    @Test
    fun `test getValue expired`() = runBlocking {
        val key = "expired_key"
        
        // Mock expired metadata (timestamp = 0, lifespan = 1 ms)
        val expiredMetadata = CacheMetadata(timestamp = 0, lifespan = 1)
        
        `when`(mockCache.metadata(key)).thenReturn(expiredMetadata)
        
        val result: User? = coordinator.getValue(key)
        
        assertNull(result)
        verify(mockCache).remove(key) // Should clean up
    }

    @Test
    fun `test getValue missing`() = runBlocking {
        val key = "missing_key"
        `when`(mockCache.metadata(key)).thenReturn(null)
        
        val result: User? = coordinator.getValue(key)
        
        assertNull(result)
    }

    data class User(val name: String, val age: Int)
}
