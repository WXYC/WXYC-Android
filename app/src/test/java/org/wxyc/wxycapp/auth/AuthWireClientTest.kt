package org.wxyc.wxycapp.auth

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class AuthWireClientTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client(fingerprint: String? = FINGERPRINT) = AuthWireClient(
        httpClient = OkHttpClient(),
        baseUrl = server.url("/").toString().trimEnd('/'),
        userAgent = "WXYC-Android/1.4",
        fingerprint = { fingerprint },
    )

    @Test
    fun `signs in anonymously with the identifying headers`() {
        server.enqueue(MockResponse().setBody("""{"token":"session-1","user":{"id":"user-1"}}"""))

        val result = client().signInAnonymously()

        assertEquals(AnonymousSignIn(sessionToken = "session-1", userId = "user-1"), result)
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/auth/sign-in/anonymous", recorded.path)
        assertEquals(server.url("/").toString().trimEnd('/'), recorded.getHeader("Origin"))
        assertEquals("WXYC-Android/1.4", recorded.getHeader("User-Agent"))
        assertEquals(FINGERPRINT, recorded.getHeader("X-Device-Fingerprint"))
        assertNull(recorded.getHeader("Authorization"))
    }

    @Test
    fun `accepts 201 from sign-in`() {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"token":"s","user":{"id":"u"}}"""))

        assertEquals("s", client().signInAnonymously().sessionToken)
    }

    @Test
    fun `mints a JWT with the session token as bearer`() {
        server.enqueue(MockResponse().setBody("""{"token":"jwt-1"}"""))

        assertEquals("jwt-1", client().mintJwt("session-1"))
        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/auth/token", recorded.path)
        assertEquals("Bearer session-1", recorded.getHeader("Authorization"))
        assertEquals(FINGERPRINT, recorded.getHeader("X-Device-Fingerprint"))
    }

    @Test
    fun `omits the fingerprint header when none is available`() {
        server.enqueue(MockResponse().setBody("""{"token":"jwt-1"}"""))

        client(fingerprint = null).mintJwt("session-1")

        assertNull(server.takeRequest().getHeader("X-Device-Fingerprint"))
    }

    @Test
    fun `reports a non-success status with its code`() {
        for (code in listOf(401, 404, 429, 500)) {
            server.enqueue(MockResponse().setResponseCode(code))
            val error = assertThrows(AuthHttpException::class.java) { client().mintJwt("session-1") }
            assertEquals(code, error.statusCode)
        }
    }

    @Test
    fun `reports a body without the expected fields as malformed`() {
        val bodies = listOf("", "[]", """{"token":""}""", """{"token":"s"}""", """{"token":"s","user":{}}""")
        for (body in bodies) {
            server.enqueue(MockResponse().setBody(body))
            assertThrows("for '$body'", AuthResponseException::class.java) { client().signInAnonymously() }
        }
    }

    @Test
    fun `error messages never carry the session token`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("session-1"))

        val error = assertThrows(AuthHttpException::class.java) { client().mintJwt("session-1") }

        assertEquals(false, error.message.orEmpty().contains("session-1"))
    }

    @Test
    fun `carries Retry-After seconds on a failure`() {
        val cases = listOf("120" to 120L, " 7 " to 7L, "Wed, 21 Oct 2026 07:28:00 GMT" to null, null to null, "-5" to null)
        for ((header, expected) in cases) {
            server.enqueue(MockResponse().setResponseCode(429).apply { header?.let { setHeader("Retry-After", it) } })
            val error = assertThrows(AuthHttpException::class.java) { client().signInAnonymously() }
            assertEquals("for '$header'", expected, error.retryAfterSeconds)
        }
    }

    private companion object {
        const val FINGERPRINT = "1b4e28ba-2fa1-11d2-883f-0016d3cca427"
    }
}
