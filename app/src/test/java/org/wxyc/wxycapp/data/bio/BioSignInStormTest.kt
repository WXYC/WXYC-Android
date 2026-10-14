package org.wxyc.wxycapp.data.bio

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.wxyc.wxycapp.auth.AnonymousAuthenticator
import org.wxyc.wxycapp.auth.AuthInterceptor
import org.wxyc.wxycapp.auth.AuthWireClient
import org.wxyc.wxycapp.auth.FakeSessionStore
import org.wxyc.wxycapp.auth.fakeJwt
import java.util.concurrent.atomic.AtomicInteger

/**
 * The whole stack a detail sheet uses, against a server whose auth is failing. Anonymous
 * sign-in shares Backend-Service's per-IP bucket (10 per 15 minutes) with DJ password
 * sign-in, so one sheet open must never cost more than one sign-in attempt.
 */
class BioSignInStormTest {

    private lateinit var server: MockWebServer
    private val signIns = AtomicInteger()
    private val mints = AtomicInteger()
    private val proxyCalls = AtomicInteger()

    /** What sign-in and mint answer; 200 means success. */
    @Volatile private var signInStatus = 200
    @Volatile private var mintStatus = 200

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.requestUrl!!.encodedPath) {
                "/auth/sign-in/anonymous" -> {
                    val n = signIns.incrementAndGet()
                    if (signInStatus != 200) MockResponse().setResponseCode(signInStatus)
                    else MockResponse().setBody("""{"token":"session-$n","user":{"id":"user-$n"}}""")
                }
                "/auth/token" -> {
                    mints.incrementAndGet()
                    if (mintStatus != 200) MockResponse().setResponseCode(mintStatus)
                    else MockResponse().setBody("""{"token":"${fakeJwt(exp = 2_000_000_000L)}"}""")
                }
                else -> {
                    proxyCalls.incrementAndGet()
                    if (request.requestUrl!!.encodedPath == "/proxy/metadata/album") MockResponse().setBody("{}")
                    else MockResponse().setResponseCode(401)
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun bioClient(): ArtistBioClient {
        val base = server.url("/").toString().trimEnd('/')
        val authenticator = AnonymousAuthenticator(
            wire = AuthWireClient(OkHttpClient(), base, "WXYC-Android/1.4", fingerprint = { null }),
            store = FakeSessionStore(),
            clock = { 1_000_000L },
            monotonicClock = { 0L },
        )
        val authed = OkHttpClient.Builder().addInterceptor(AuthInterceptor(authenticator, server.hostName)).build()
        return ArtistBioClient(authed, base)
    }

    /** A bio with 25 id references: the worst case for the resolve pass. */
    private val bio = (1..25).joinToString(", ") { "[a$it]" }

    /** Opens [sheets] detail sheets in a row, each for a different playcut, on one app session. */
    private fun openSheets(sheets: Int): List<BioTokensResult> {
        val client = bioClient()
        return (1..sheets).map { n -> runBlocking { client.renderedBio("Artist $n", "Release $n", bio) } }
    }

    @Test
    fun `a rate-limited sign-in is attempted once across many sheet opens`() {
        signInStatus = 429

        val results = openSheets(10)

        assertEquals(List(10) { BioTokensResult.Failed }, results)
        assertEquals(1, signIns.get())
        assertEquals(0, proxyCalls.get())
    }

    @Test
    fun `a server that rejects every new session's first mint costs one sign-in across many sheet opens`() {
        for (status in listOf(401, 404)) {
            signIns.set(0)
            mints.set(0)
            mintStatus = status

            assertEquals(List(10) { BioTokensResult.Failed }, openSheets(10))

            assertEquals("mint $status", 1, signIns.get())
            assertEquals("mint $status", 1, mints.get())
        }
        assertEquals(0, proxyCalls.get())
    }

    @Test
    fun `a failing mint keeps its session, so many sheet opens create one anonymous user`() {
        mintStatus = 500

        assertEquals(List(10) { BioTokensResult.Failed }, openSheets(10))

        assertEquals(1, signIns.get())
        assertEquals(0, proxyCalls.get())
    }

    @Test
    fun `a resolve pass whose calls are rejected costs at most one sign-in`() {
        // Auth works until the proxy starts rejecting every JWT mid-pass.
        assertEquals(BioTokensResult.Failed, openSheets(1).single())

        assertEquals(1, signIns.get())
    }
}
