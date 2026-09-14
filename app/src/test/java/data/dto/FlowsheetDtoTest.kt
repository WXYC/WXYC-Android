package data.dto

import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import data.WxycApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.http.Query

/**
 * Exercises the actual wire shape from api.wxyc.org/flowsheet (sampled live on
 * 2026-09-12) rather than hand-built objects, since the snake_case -> camelCase
 * Gson mapping and the entry_type branching are exactly what a typo in either
 * would sail past silently (WXYC-Android PR #71 review).
 */
class FlowsheetDtoTest {

    private val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    private fun parse(json: String): FlowsheetEntryDto =
        gson.fromJson(json, FlowsheetEntryDto::class.java)

    @Test
    fun `WxycApi requests the server's limit parameter, not n`() {
        // A prior version queried ?n=, which the server silently ignores in favor
        // of its own default of 30 rows regardless of what the caller asked for.
        val method = WxycApi::class.java.declaredMethods.single { it.name == "getFlowsheet" }
        val query = method.parameterAnnotations.flatten().filterIsInstance<Query>().single()
        assertEquals("limit", query.value)
    }

    @Test
    fun `track entry maps title, artist, and streaming metadata`() {
        val json = """
            {"id":5318252,"show_id":1951362,"play_order":20,
             "add_time":"2026-09-12T20:45:25.524Z","entry_type":"track",
             "artist_name":"DJ Food","album_title":"ninja tune trip hop and jazz",
             "track_title":"ninja walk","record_label":"instinct records",
             "artwork_url":"https://i.discogs.com/x.jpeg",
             "spotify_url":"https://open.spotify.com/track/6vifb1Bh7078BRcVixLRyQ"}
        """.trimIndent()

        val playcut = parse(json).toDomain()

        assertEquals("track", playcut.entryType)
        assertEquals("ninja walk", playcut.songTitle)
        assertEquals("DJ Food", playcut.artistName)
        assertEquals("ninja tune trip hop and jazz", playcut.releaseTitle)
        assertEquals("instinct records", playcut.labelName)
        assertEquals(1951362, playcut.showId)
        assertEquals(20, playcut.playOrder)
        assertEquals(
            "https://open.spotify.com/track/6vifb1Bh7078BRcVixLRyQ",
            playcut.metadata?.spotifyURL
        )
        assertNull("a track row has no marker label", playcut.displayMessage)
    }

    @Test
    fun `breakpoint prefers the server's own message over a derived hour label`() {
        val json = """
            {"id":5318240,"show_id":1951362,"play_order":8,
             "add_time":"2026-09-12T20:00:08.719Z","entry_type":"breakpoint",
             "message":"4:00 PM Breakpoint","radio_hour":null}
        """.trimIndent()

        assertEquals("4:00 PM Breakpoint", parse(json).toDomain().displayMessage)
    }

    @Test
    fun `breakpoint falls back to a derived hour label when the server sends no message`() {
        // 19:59:15 UTC is 3:59:15 PM EDT, one minute before the 4 PM breakpoint it
        // marks — the exact case api.yaml documents radio_hour as covering.
        val json = """
            {"id":1,"show_id":1,"play_order":1,
             "add_time":"2026-09-12T19:59:15.248Z","entry_type":"breakpoint"}
        """.trimIndent()

        assertEquals("3 PM", parse(json).toDomain().displayMessage)
    }

    @Test
    fun `talkset and message entries surface the server's message text`() {
        val talkset = parse(
            """{"id":1,"show_id":1,"play_order":1,"add_time":"2026-09-12T20:44:22.679Z","entry_type":"talkset","message":"Talkset"}"""
        ).toDomain()
        assertEquals("Talkset", talkset.displayMessage)

        val message = parse(
            """{"id":2,"show_id":1,"play_order":2,"add_time":"2026-09-12T20:44:22.679Z","entry_type":"message","message":"Back after this"}"""
        ).toDomain()
        assertEquals("Back after this", message.displayMessage)
    }

    @Test
    fun `show and DJ marker entries render a label from dj_name`() {
        val showStart = parse(
            """{"id":1,"show_id":1,"play_order":1,"add_time":"2026-09-12T19:35:02.405Z","entry_type":"show_start","dj_name":"DJ June","timestamp":"9/12/2026, 3:35:02 PM"}"""
        ).toDomain()
        assertEquals("DJ June signed on", showStart.displayMessage)

        val showEnd = parse(
            """{"id":2,"show_id":1,"play_order":2,"add_time":"2026-09-12T19:28:50.114Z","entry_type":"show_end","dj_name":"DJ Chowder","timestamp":"9/12/2026, 3:28:50 PM"}"""
        ).toDomain()
        assertEquals("DJ Chowder signed off", showEnd.displayMessage)

        val djJoin = parse(
            """{"id":3,"show_id":1,"play_order":3,"add_time":"2026-09-12T19:28:50.114Z","entry_type":"dj_join","dj_name":"DJ Chowder"}"""
        ).toDomain()
        assertEquals("DJ Chowder joined", djJoin.displayMessage)

        val djLeave = parse(
            """{"id":4,"show_id":1,"play_order":4,"add_time":"2026-09-12T19:28:50.114Z","entry_type":"dj_leave","dj_name":"DJ Chowder"}"""
        ).toDomain()
        assertEquals("DJ Chowder left", djLeave.displayMessage)
    }

    @Test
    fun `hour parses to null instead of masquerading as now on malformed input`() {
        val json = """{"id":1,"show_id":1,"play_order":1,"add_time":"not-a-timestamp","entry_type":"talkset","message":"Talkset"}"""
        assertNull(parse(json).toDomain().hour)
    }
}
