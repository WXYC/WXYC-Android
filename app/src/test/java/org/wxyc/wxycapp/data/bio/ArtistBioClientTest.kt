package org.wxyc.wxycapp.data.bio

import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

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

    private fun client() = ArtistBioClient(OkHttpClient(), server.url("/").toString().trimEnd('/'))

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
            MockResponse().setBody("""{"bioTokens":[]}"""),
            MockResponse().setBody("""{"bioTokens":[{"type":"poem"}]}"""),
            MockResponse().setResponseCode(404),
        )
        for (response in responses) {
            enqueueAlbum(discogsArtistId = 1)
            server.enqueue(response)
            assertEquals(response.toString(), BioTokensResult.NoTokens, client().bioTokens("Stereolab", null))
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
}
