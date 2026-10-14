package org.wxyc.wxycapp.data.bio

import org.junit.Assert.assertEquals
import org.junit.Test

/** Id references resolved through `proxy/entity/resolve`, matching iOS's async parser. */
class DiscogsMarkupParserResolutionTest {

    @Test
    fun `lists the artist, release and master ids a bio references, once each`() {
        val bio = "[a=Stereolab] formed by [a1001] and [a1002], then [a1001] again; see [r2001], [r=2002], [m3001], [m=3002] on [l4001]."

        assertEquals(
            setOf(
                EntityRef(EntityRef.Type.ARTIST, 1001),
                EntityRef(EntityRef.Type.ARTIST, 1002),
                EntityRef(EntityRef.Type.RELEASE, 2001),
                EntityRef(EntityRef.Type.RELEASE, 2002),
                EntityRef(EntityRef.Type.MASTER, 3001),
                EntityRef(EntityRef.Type.MASTER, 3002),
            ),
            DiscogsMarkupParser.entityReferences(bio),
        )
    }

    @Test
    fun `renders resolved ids as links`() {
        val bio = "Founded by [a1001]. Debut: [r=2001]; best known for [m3001]."
        val names = mapOf(
            EntityRef(EntityRef.Type.ARTIST, 1001) to "Lætitia Sadier (2)",
            EntityRef(EntityRef.Type.RELEASE, 2001) to "Peng!",
            EntityRef(EntityRef.Type.MASTER, 3001) to "Dots And Loops",
        )

        assertEquals(
            listOf(
                BioToken.PlainText("Founded by "),
                BioToken.ArtistLink("Lætitia Sadier (2)", "Lætitia Sadier", "https://www.discogs.com/artist/1001"),
                BioToken.PlainText(". Debut: "),
                BioToken.UrlLink("https://www.discogs.com/release/2001", "Peng!"),
                BioToken.PlainText("; best known for "),
                BioToken.UrlLink("https://www.discogs.com/master/3001", "Dots And Loops"),
                BioToken.PlainText("."),
            ),
            DiscogsMarkupParser.parse(bio, names),
        )
    }

    @Test
    fun `drops the ids it has no name for, as before`() {
        val bio = "American rap group founded by [a56678] and DJ Headliner."
        val unrelated = mapOf(EntityRef(EntityRef.Type.ARTIST, 1) to "Stereolab")

        for (names in listOf(emptyMap(), unrelated)) {
            assertEquals(
                "American rap group founded by and DJ Headliner.",
                DiscogsMarkupParser.parse(bio, names).joinToString("") { it.text },
            )
        }
    }
}
