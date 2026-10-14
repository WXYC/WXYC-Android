package org.wxyc.wxycapp.auth

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** A [SessionStore] double that can be told to fail. */
internal class FakeSessionStore(var stored: AuthSession? = null) : SessionStore {
    var failSaves = false

    override fun load(): AuthSession? = stored

    override fun save(session: AuthSession) {
        if (failSaves) throw IOException("disk full")
        stored = session
    }

    override fun clear() {
        stored = null
    }
}

class AnonymousAuthenticatorTest {

    private lateinit var server: MockWebServer
    private val signIns = AtomicInteger()
    private val mints = AtomicInteger()
    private val mintedFor = mutableListOf<String>()

    /**
     * Status `/auth/token` answers with for the pre-existing "stored-session" token. Every
     * other session token gets a JWT that expires an hour after [now].
     */
    @Volatile private var storedSessionStatus = 200
    private var now = 1_000_000L

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/auth/sign-in/anonymous" -> {
                    val n = signIns.incrementAndGet()
                    MockResponse().setBody("""{"token":"session-$n","user":{"id":"user-$n"}}""")
                }
                "/auth/token" -> {
                    val n = mints.incrementAndGet()
                    val bearer = request.getHeader("Authorization")!!.removePrefix("Bearer ")
                    synchronized(mintedFor) { mintedFor += bearer }
                    val status = if (bearer == "stored-session") storedSessionStatus else 200
                    if (status != 200) MockResponse().setResponseCode(status)
                    else MockResponse().setBody("""{"token":"${fakeJwt(exp = now + 3_600, subject = "jwt-$n")}"}""")
                }
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun authenticator(store: SessionStore = FakeSessionStore()) = AnonymousAuthenticator(
        wire = AuthWireClient(
            httpClient = OkHttpClient(),
            baseUrl = server.url("/").toString().trimEnd('/'),
            userAgent = "WXYC-Android/1.4",
            fingerprint = { null },
        ),
        store = store,
        clock = { now },
    )

    private fun storedSession(jwtExpiresAt: Long) = AuthSession(
        sessionToken = "stored-session",
        jwt = fakeJwt(exp = jwtExpiresAt, subject = "stored"),
        userId = "stored-user",
        jwtExpiresAtEpochSeconds = jwtExpiresAt,
    )

    @Test
    fun `signs in once, mints, persists, then serves from memory`() {
        val store = FakeSessionStore()
        val auth = authenticator(store)

        val first = auth.token()
        val second = auth.token()

        assertEquals(first, second)
        assertEquals(1, signIns.get())
        assertEquals(1, mints.get())
        assertEquals(listOf("session-1"), mintedFor)
        assertEquals("user-1", store.stored?.userId)
        assertEquals(first, store.stored?.jwt)
    }

    @Test
    fun `uses a fresh stored JWT with no network call`() {
        val stored = storedSession(jwtExpiresAt = now + 600)

        assertEquals(stored.jwt, authenticator(FakeSessionStore(stored)).token())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `re-mints a stale stored JWT and keeps the same anonymous user`() {
        val store = FakeSessionStore(storedSession(jwtExpiresAt = now + 30))

        authenticator(store).token()

        assertEquals(0, signIns.get())
        assertEquals(listOf("stored-session"), mintedFor)
        assertEquals("stored-user", store.stored?.userId)
    }

    @Test
    fun `refreshes the in-memory JWT once it goes stale`() {
        val auth = authenticator()
        val first = auth.token()

        now += 3_600 - 59
        val second = auth.token()

        assertNotEquals(first, second)
        assertEquals(1, signIns.get())
        assertEquals(listOf("session-1", "session-1"), mintedFor)
    }

    @Test
    fun `signs in again when the stored session is rejected`() {
        for (status in listOf(401, 404)) {
            signIns.set(0)
            storedSessionStatus = status
            val store = FakeSessionStore(storedSession(jwtExpiresAt = now - 1))

            authenticator(store).token()

            assertEquals("after $status", 1, signIns.get())
            assertEquals("after $status", "user-1", store.stored?.userId)
        }
    }

    @Test
    fun `other mint failures propagate and keep the stored session`() {
        val stored = storedSession(jwtExpiresAt = now - 1)
        val store = FakeSessionStore(stored)
        storedSessionStatus = 503

        val error = assertThrows(AuthHttpException::class.java) { authenticator(store).token() }

        assertEquals(503, error.statusCode)
        assertEquals(stored, store.stored)
        assertEquals(0, signIns.get())
    }

    @Test
    fun `concurrent callers share one sign-in`() {
        val auth = authenticator()
        val pool = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        val tokens = (1..8).map { pool.submit<String> { start.await(); auth.token() } }

        start.countDown()
        val results = tokens.map { it.get(10, TimeUnit.SECONDS) }.toSet()
        pool.shutdown()

        assertEquals(1, results.size)
        assertEquals(1, signIns.get())
        assertEquals(1, mints.get())
    }

    @Test
    fun `reauthenticating re-mints from the session even when the JWT looks fresh`() {
        val auth = authenticator()
        val rejected = auth.token()

        val replacement = auth.reauthenticate(rejected)

        assertNotEquals(rejected, replacement)
        assertEquals(1, signIns.get())
        assertEquals(listOf("session-1", "session-1"), mintedFor)
    }

    @Test
    fun `reauthenticating hands back a JWT another caller already replaced`() {
        val auth = authenticator()
        val rejected = auth.token()
        val replacement = auth.reauthenticate(rejected)

        assertEquals(replacement, auth.reauthenticate(rejected))
        assertEquals(2, mints.get())
    }

    @Test
    fun `a failed save still leaves a usable in-memory session`() {
        val store = FakeSessionStore().apply { failSaves = true }
        val auth = authenticator(store)

        val token = auth.token()

        assertNull(store.stored)
        assertEquals(token, auth.token())
        assertEquals(1, signIns.get())
    }

    @Test
    fun `a stale in-memory session is re-minted when storage never persisted it`() {
        val store = FakeSessionStore().apply { failSaves = true }
        val auth = authenticator(store)
        auth.token()

        now += 3_600
        auth.token()

        assertEquals(1, signIns.get())
        assertEquals(listOf("session-1", "session-1"), mintedFor)
    }
}
