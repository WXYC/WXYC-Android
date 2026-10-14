package org.wxyc.wxycapp.auth

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.io.IOException

private class FakeTokenProvider : BearerTokenProvider {
    var current = "jwt-1"
    val rejected = mutableListOf<String>()
    var failToken = false

    override fun token(): String {
        if (failToken) throw AuthHttpException(503)
        return current
    }

    override fun reauthenticate(rejectedJwt: String): String {
        rejected += rejectedJwt
        current = "jwt-${rejected.size + 1}"
        return current
    }
}

class AuthInterceptorTest {

    private lateinit var server: MockWebServer
    private val tokens = FakeTokenProvider()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun get(): Int {
        val client = OkHttpClient.Builder().addInterceptor(AuthInterceptor(tokens)).build()
        return client.newCall(Request.Builder().url(server.url("/proxy/metadata/artist")).build())
            .execute().use { it.code }
    }

    private fun authorizationHeaders() = (1..server.requestCount).map { server.takeRequest().getHeader("Authorization") }

    @Test
    fun `adds the bearer JWT`() {
        server.enqueue(MockResponse())

        assertEquals(200, get())
        assertEquals(listOf("Bearer jwt-1"), authorizationHeaders())
    }

    @Test
    fun `retries once with a fresh JWT after a 401`() {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse())

        assertEquals(200, get())
        assertEquals(listOf("jwt-1"), tokens.rejected)
        assertEquals(listOf("Bearer jwt-1", "Bearer jwt-2"), authorizationHeaders())
    }

    @Test
    fun `returns the second 401 instead of retrying again`() {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(401))

        assertEquals(401, get())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `does not retry other failures`() {
        for (code in listOf(403, 429, 500)) {
            server.enqueue(MockResponse().setResponseCode(code))
            assertEquals(code, get())
        }
        assertEquals(3, server.requestCount)
        assertEquals(emptyList<String>(), tokens.rejected)
    }

    @Test
    fun `surfaces an auth failure as an IOException without calling the server`() {
        tokens.failToken = true

        assertThrows(IOException::class.java) { get() }
        assertEquals(0, server.requestCount)
    }
}
