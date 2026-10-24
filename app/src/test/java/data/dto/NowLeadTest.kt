package data.dto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.wxyc.wxycapp.data.Playcut

class NowLeadTest {

    private fun song(playOrder: Int, showId: Int = 7, art: String? = "https://i.discogs.com/x.jpeg") = Playcut(
        id = playOrder, entryType = "track", hour = null, showId = showId, playOrder = playOrder,
        songTitle = "la paradoja", artistName = "Juana Molina", imageURL = art
    )

    private fun entry(role: String, playOrder: Int, showId: Int = 7, name: String = "Cat Power") =
        FlowsheetEntryDto(
            id = 100 + playOrder, showId = showId, playOrder = playOrder, addTime = "2026-09-12T20:44:22.679Z",
            entryType = "spotlight", spotlightRole = role, spotlightName = name
        )

    private fun live(spotlight: FlowsheetEntryDto?, showId: Int? = 7) =
        OnAir.Live(djName = "DJ Sidechain", showId = showId, spotlight = spotlight)

    private class Case(
        val name: String,
        val playcuts: List<Playcut>,
        val onAir: OnAir,
        val expected: NowMetadata?
    )

    private val songLead = NowMetadata(Lead.SONG, "la paradoja", "Juana Molina", "https://i.discogs.com/x.jpeg")
    private val songNoArt = songLead.copy(artworkUrl = null)

    private fun entryLead(title: String) = NowMetadata(Lead.ENTRY, title, "DJ Sidechain", null)

    @Test
    fun `lead rule table`() {
        val cases = listOf(
            Case("song newer than entry", listOf(song(10)), live(entry("guest", 5)), songLead),
            Case("entry newer than song", listOf(song(5)), live(entry("guest", 10)),
                entryLead("In the studio: Cat Power")),
            Case("entry with no song in the open show", listOf(song(50, showId = 3)), live(entry("performer", 1)),
                entryLead("Live in the studio: Cat Power")),
            Case("entry with no songs at all", emptyList(), live(entry("reader", 1)),
                entryLead("Reading now: Cat Power")),
            Case("host entry never leads", listOf(song(5)), live(entry("host", 10)), songLead),
            Case("host entry with no song leaves nothing to show", emptyList(), live(entry("host", 10)), null),
            Case("entry from a previous show", listOf(song(5)), live(entry("guest", 10, showId = 3)), songLead),
            Case("unknown role", listOf(song(5)), live(entry("future_role", 10)),
                entryLead("On air: Cat Power")),
            Case("null on-air show id", listOf(song(5)), live(entry("guest", 10), showId = null), songLead),
            Case("zero show id on both sides", listOf(song(5, showId = 0)), live(entry("guest", 10, showId = 0), showId = 0), songLead),
            Case("song without artwork falls back to station artwork", listOf(song(10, art = null)), live(null), songNoArt),
            Case("no spotlight", listOf(song(10)), live(null), songLead),
            Case("automation", listOf(song(10)), OnAir.Automation, songLead),
            Case("unknown on-air", listOf(song(10)), OnAir.Unknown, songLead),
            Case("newest track wins over markers", listOf(Playcut(1, "talkset", null, 7, 12), song(10)), live(null), songLead),
            Case("nothing to show", emptyList(), OnAir.Automation, null),
        )
        for (c in cases) assertEquals(c.name, c.expected, nowLead(c.playcuts, c.onAir))
    }

    @Test
    fun `an entry never carries the previous song's artwork`() {
        val lead = nowLead(listOf(song(5)), live(entry("guest", 10)))
        assertNull(lead?.artworkUrl)
    }
}
