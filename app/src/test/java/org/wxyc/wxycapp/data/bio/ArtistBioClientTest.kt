package org.wxyc.wxycapp.data.bio

import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class ArtistBioClientTest {

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

    private var now = 1_000_000L

    private fun client() = ArtistBioClient(OkHttpClient(), server.url("/").toString().trimEnd('/'), clock = { now })

    private fun enqueueAlbum(discogsArtistId: Int?) {
        val id = discogsArtistId?.let { ""","discogsArtistId":$it""" } ?: ""
        server.enqueue(MockResponse().setBody("""{"artistName":"Stereolab"$id}"""))
    }

    @Test
    fun `looks up the artist id from the album, then fetches its bio tokens`() {
        enqueueAlbum(discogsArtistId = 3_877)
        server.enqueue(
            MockResponse().setBody(
                """{"discogsArtistId":3877,"bio":"raw","bioTokens":[{"type":"plainText","text":"Formed in London."}]}"""
            )
        )

        val result = client().bioTokens(artistName = "Stereolab", releaseTitle = "Aluminum Tunes")

        assertEquals(BioTokensResult.Found(listOf(BioToken.PlainText("Formed in London."))), result)
        val album = server.takeRequest().requestUrl!!
        assertEquals("/proxy/metadata/album", album.encodedPath)
        assertEquals("Stereolab", album.queryParameter("artistName"))
        assertEquals("Aluminum Tunes", album.queryParameter("releaseTitle"))
        val artist = server.takeRequest().requestUrl!!
        assertEquals("/proxy/metadata/artist", artist.encodedPath)
        assertEquals("3877", artist.queryParameter("artistId"))
    }

    @Test
    fun `omits the release title when there is none`() {
        enqueueAlbum(discogsArtistId = null)

        client().bioTokens(artistName = "Stereolab", releaseTitle = null)

        assertNull(server.takeRequest().requestUrl!!.queryParameter("releaseTitle"))
    }

    @Test
    fun `reports no tokens, without an artist call, when the album has no Discogs artist or is not found`() {
        enqueueAlbum(discogsArtistId = null)
        server.enqueue(MockResponse().setResponseCode(404))

        assertEquals(BioTokensResult.NoTokens, client().bioTokens("Stereolab", "Aluminum Tunes"))
        assertEquals(BioTokensResult.NoTokens, client().bioTokens("Stereolab", "Aluminum Tunes"))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `reports no tokens when the artist has none or is not found`() {
        val responses = listOf(
            MockResponse().setBody("""{"bio":"raw"}"""),
            MockResponse().setBody("""{"bioTokens":null}"""),
            MockResponse().setResponseCode(404),
        )
        for (response in responses) {
            enqueueAlbum(discogsArtistId = 1)
            server.enqueue(response)
            assertEquals(response.toString(), BioTokensResult.NoTokens, client().bioTokens("Stereolab", null))
        }
    }

    @Test
    fun `treats any token array as final, even one with no text, as iOS does`() {
        for (body in listOf("""{"bioTokens":[]}""", """{"bioTokens":[{"type":"poem"}]}""", """{"bioTokens":[{"type":"plainText","text":""}]}""")) {
            enqueueAlbum(discogsArtistId = 1)
            server.enqueue(MockResponse().setBody(body))
            assertTrue(body, client().bioTokens("Stereolab", null) is BioTokensResult.Found)
        }
    }

    @Test
    fun `reports a failure, distinct from no tokens, when either call fails`() {
        val failures = listOf(
            MockResponse().setResponseCode(401),
            MockResponse().setResponseCode(404),
            MockResponse().setResponseCode(429),
            MockResponse().setBody("not json"),
            MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START),
        )
        val notFound = failures.filterNot { it.status.contains("404") }
        for (failure in notFound) {
            server.enqueue(failure)
            assertEquals("album $failure", BioTokensResult.Failed, client().bioTokens("Stereolab", null))
        }
        for (failure in notFound) {
            enqueueAlbum(discogsArtistId = 1)
            server.enqueue(failure)
            assertEquals("artist $failure", BioTokensResult.Failed, client().bioTokens("Stereolab", null))
        }
    }

    @Test
    fun `decodes each token type the way iOS does`() {
        val cases = listOf(
            """{"type":"plainText","text":"Line one\r\nLine two"}""" to BioToken.PlainText("Line one\nLine two"),
            """{"type":"artistLink","name":"Juana Molina (2)","display_name":"Juana Molina","url":"https://www.discogs.com/artist/1"}""" to
                BioToken.ArtistLink("Juana Molina (2)", "Juana Molina", "https://www.discogs.com/artist/1"),
            """{"type":"labelName","name":"Drag City"}""" to BioToken.LabelName("Drag City"),
            """{"type":"releaseLink","title":"DOGA","url":"https://www.discogs.com/release/2"}""" to
                BioToken.UrlLink("https://www.discogs.com/release/2", "DOGA"),
            """{"type":"masterLink","title":"Edits","url":"https://www.discogs.com/master/3"}""" to
                BioToken.UrlLink("https://www.discogs.com/master/3", "Edits"),
            """{"type":"bold","content":"Jessica Pratt"}""" to BioToken.Bold("Jessica Pratt"),
            """{"type":"italic","content":"On Your Own Love Again"}""" to BioToken.Italic("On Your Own Love Again"),
            """{"type":"underline","content":"Sonamos"}""" to BioToken.Underline("Sonamos"),
            """{"type":"urlLink","href":"https://stereolab.co.uk","content":"site"}""" to
                BioToken.UrlLink("https://stereolab.co.uk", "site"),
            """{"type":"urlLink","href":null,"content":"site"}""" to BioToken.UrlLink(null, "site"),
            // A token missing the fields its type needs falls back to its best text.
            """{"type":"artistLink","name":"Stereolab"}""" to BioToken.PlainText("Stereolab"),
            """{"type":"bold"}""" to BioToken.PlainText(""),
            // A link that is not http(s) keeps its text and loses the link.
            """{"type":"releaseLink","title":"DOGA","url":"javascript:alert(1)"}""" to BioToken.PlainText("DOGA"),
            """{"type":"urlLink","href":"intent://x","content":"site"}""" to BioToken.UrlLink(null, "site"),
        )
        for ((json, expected) in cases) {
            assertEquals(json, expected, ArtistBioClient.decodeToken(JsonParser.parseString(json).asJsonObject))
        }
    }

    @Test
    fun `drops unknown token types`() {
        assertNull(ArtistBioClient.decodeToken(JsonParser.parseString("""{"type":"poem","text":"x"}""").asJsonObject))
    }

    /** Serves an album with [discogsArtistId], an artist with [bioTokens], and [names] by "type-id". */
    private fun route(discogsArtistId: Int?, bioTokens: String?, names: Map<String, String>, resolveStatus: Int = 404) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl!!
                return when (url.encodedPath) {
                    "/proxy/metadata/album" ->
                        MockResponse().setBody(discogsArtistId?.let { """{"discogsArtistId":$it}""" } ?: "{}")
                    "/proxy/metadata/artist" -> MockResponse().setBody("""{"bioTokens":${bioTokens ?: "null"}}""")
                    "/proxy/entity/resolve" -> names["${url.queryParameter("type")}-${url.queryParameter("id")}"]
                        ?.let { MockResponse().setBody("""{"name":"$it","type":"artist","id":1}""") }
                        ?: MockResponse().setResponseCode(resolveStatus)
                    else -> MockResponse().setResponseCode(500)
                }
            }
        }
    }

    private fun paths() = (1..server.requestCount).map { server.takeRequest().requestUrl!!.encodedPath }

    @Test
    fun `renders the server tokens when there are any, without resolving ids`() {
        route(discogsArtistId = 7, bioTokens = """[{"type":"plainText","text":"From London."}]""", names = mapOf("artist-1001" to "x"))

        val rendered = runBlocking { client().renderedBio("Stereolab", null, "Founded by [a1001].") }

        assertEquals(BioTokensResult.Found(listOf(BioToken.PlainText("From London."))), rendered)
        assertEquals(listOf("/proxy/metadata/album", "/proxy/metadata/artist"), paths())
    }

    @Test
    fun `otherwise resolves the raw bio's ids and renders them as links`() {
        route(discogsArtistId = null, bioTokens = null, names = mapOf("artist-1001" to "Lætitia Sadier"))

        val rendered = runBlocking { client().renderedBio("Stereolab", null, "Founded by [a1001].") }

        assertEquals(
            BioTokensResult.Found(
                listOf(
                    BioToken.PlainText("Founded by "),
                    BioToken.ArtistLink("Lætitia Sadier", "Lætitia Sadier", "https://www.discogs.com/artist/1001"),
                    BioToken.PlainText("."),
                )
            ),
            rendered,
        )
    }

    @Test
    fun `reports no tokens when the bio has no ids or none resolve`() {
        for (bio in listOf("Formed in London.", "Founded by [a1001].")) {
            route(discogsArtistId = null, bioTokens = null, names = emptyMap())
            assertEquals(bio, BioTokensResult.NoTokens, runBlocking { client().renderedBio("Stereolab", null, bio) })
        }
    }

    @Test
    fun `does not resolve ids when the token lookup failed`() {
        server.enqueue(MockResponse().setResponseCode(500))

        val rendered = runBlocking { client().renderedBio("Stereolab", null, "Founded by [a1001].") }

        assertEquals(BioTokensResult.Failed, rendered)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `stops resolving at the first failure, at most four at a time`() {
        route(discogsArtistId = null, bioTokens = null, names = emptyMap(), resolveStatus = 500)
        val bio = (1..12).joinToString(" ") { "[a$it]" }

        val rendered = runBlocking { client().renderedBio("Stereolab", null, bio) }

        assertEquals(BioTokensResult.Failed, rendered)
        assertEquals(4, paths().count { it == "/proxy/entity/resolve" })
    }

    @Test
    fun `keeps what resolved before a failure`() {
        route(discogsArtistId = null, bioTokens = null, names = (1..4).associate { "artist-$it" to "Name $it" }, resolveStatus = 500)
        val bio = (1..8).joinToString(" ") { "[a$it]" }

        val rendered = runBlocking { client().renderedBio("Stereolab", null, bio) } as BioTokensResult.Found

        assertEquals(listOf("Name 1", "Name 2", "Name 3", "Name 4"), rendered.tokens.filterIsInstance<BioToken.ArtistLink>().map { it.name })
        assertFalse("a partial result must not be kept for good", rendered.complete)
    }

    @Test
    fun `resolves at most twenty ids per bio`() {
        route(discogsArtistId = null, bioTokens = null, names = (1..30).associate { "artist-$it" to "Name $it" })
        val bio = (1..30).joinToString(" ") { "[a$it]" }

        runBlocking { client().renderedBio("Stereolab", null, bio) }

        assertEquals(20, paths().count { it == "/proxy/entity/resolve" })
    }

    @Test
    fun `resolves an entity once and remembers it`() {
        route(discogsArtistId = null, bioTokens = null, names = mapOf("master-3001" to "Dots And Loops"))
        val client = client()
        val ref = EntityRef(EntityRef.Type.MASTER, 3001)

        assertEquals("Dots And Loops", client.resolveEntity(ref))
        assertEquals("Dots And Loops", client.resolveEntity(ref))

        val recorded = server.takeRequest().requestUrl!!
        assertEquals("master", recorded.queryParameter("type"))
        assertEquals("3001", recorded.queryParameter("id"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `remembers an unknown entity for ten minutes`() {
        route(discogsArtistId = null, bioTokens = null, names = emptyMap())
        val client = client()
        val ref = EntityRef(EntityRef.Type.ARTIST, 1)

        assertNull(client.resolveEntity(ref))
        now += 599
        assertNull(client.resolveEntity(ref))
        assertEquals(1, server.requestCount)
        now += 1
        assertNull(client.resolveEntity(ref))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a failed resolution throws and is not remembered`() {
        route(discogsArtistId = null, bioTokens = null, names = emptyMap(), resolveStatus = 503)
        val client = client()
        val ref = EntityRef(EntityRef.Type.ARTIST, 1)

        repeat(2) { assertThrows(IOException::class.java) { client.resolveEntity(ref) } }
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `the name cache is bounded`() {
        val cache = EntityNameCache(maxEntries = 2, negativeTtlSeconds = 600, clock = { now })
        val refs = (1L..3L).map { EntityRef(EntityRef.Type.ARTIST, it) }
        refs.forEach { cache.put(it, "Name ${it.id}") }

        assertNull(cache.get(refs[0]))
        assertEquals(EntityNameCache.Hit("Name 3"), cache.get(refs[2]))
    }
}
