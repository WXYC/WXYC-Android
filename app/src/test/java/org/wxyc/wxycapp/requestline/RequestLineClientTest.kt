package org.wxyc.wxycapp.requestline

import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RequestLineClientTest {

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

    private val captured = mutableListOf<Pair<String, Map<String, Any>>>()

    private fun client(fingerprint: String? = FINGERPRINT) = RequestLineClient(
        httpClient = OkHttpClient(),
        endpoint = server.url("/request").toString(),
        userAgent = "WXYC-Android/1.4",
        fingerprint = { fingerprint },
        capture = { event, properties -> captured += event to properties },
    )

    private fun capturedNames() = captured.map { it.first }

    @Test
    fun `posts the message with user agent and fingerprint headers`() {
        server.enqueue(MockResponse().setResponseCode(200))

        val result = client().send("la paradoja by Juana Molina")

        assertEquals(RequestLineClient.Result.Sent, result)
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/request", recorded.path)
        assertEquals("WXYC-Android/1.4", recorded.getHeader("User-Agent"))
        assertEquals(FINGERPRINT, recorded.getHeader("X-Device-Fingerprint"))
        assertTrue(recorded.getHeader("Content-Type")!!.startsWith("application/json"))
        assertEquals(
            "la paradoja by Juana Molina",
            JsonParser.parseString(recorded.body.readUtf8()).asJsonObject["message"].asString,
        )
    }

    @Test
    fun `omits the fingerprint header when none is available`() {
        server.enqueue(MockResponse().setResponseCode(200))

        client(fingerprint = null).send("Moon Pix")

        assertNull(server.takeRequest().getHeader("X-Device-Fingerprint"))
    }

    @Test
    fun `treats a ban as sent`() {
        server.enqueue(MockResponse().setResponseCode(403))

        assertEquals(RequestLineClient.Result.Sent, client().send("Aluminum Tunes"))
    }

    @Test
    fun `reports other statuses as failures`() {
        server.enqueue(MockResponse().setResponseCode(502))

        assertEquals(RequestLineClient.Result.Failed(502), client().send("Aluminum Tunes"))
    }

    @Test
    fun `reports a dropped connection as a network error`() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        assertTrue(client().send("Aluminum Tunes") is RequestLineClient.Result.NetworkError)
    }

    @Test
    fun `records the status code of every response`() {
        server.enqueue(MockResponse().setResponseCode(502))

        client().send("Aluminum Tunes")

        assertEquals(listOf("request_line_request_completed_event"), capturedNames())
        assertEquals(502, captured.single().second["status_code"])
        assertEquals(false, captured.single().second["authenticated"])
        assertTrue(captured.single().second["duration_ms"] is Double)
    }

    @Test
    fun `records a ban even though the listener is told it was sent`() {
        server.enqueue(MockResponse().setResponseCode(403).setBody("banned"))

        assertEquals(RequestLineClient.Result.Sent, client().send("Aluminum Tunes"))
        assertEquals(
            listOf("request_line_request_completed_event", "request_line_user_banned_event"),
            capturedNames(),
        )
    }

    @Test
    fun `keeps the status code when the error body cannot be read`() {
        // Headers arrive, then the connection drops mid-body: the status is
        // known, so this must not degrade into a network error.
        server.enqueue(
            MockResponse().setResponseCode(502)
                .setBody("truncated")
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
        )

        assertEquals(RequestLineClient.Result.Failed(502), client().send("Aluminum Tunes"))
    }

    @Test
    fun `reports a non-IO failure without throwing`() {
        val exploding = RequestLineClient(
            httpClient = OkHttpClient(),
            endpoint = server.url("/request").toString(),
            userAgent = "WXYC-Android/1.4",
            fingerprint = { throw IllegalStateException("keystore unavailable") },
            capture = { event, properties -> captured += event to properties },
        )

        val result = exploding.send("Aluminum Tunes")

        assertTrue(result is RequestLineClient.Result.NetworkError)
        assertEquals(listOf("error"), capturedNames())
        assertEquals("request_line", captured.single().second["context"])
    }

    private companion object {
        const val FINGERPRINT = "11111111-2222-3333-4444-555555555555"
    }
}
