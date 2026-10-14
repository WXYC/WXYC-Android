package org.wxyc.wxycapp.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.wxyc.wxycapp.data.bio.BioToken

class ArtistBioTextTest {

    private val linkColor = Color.Gray

    @Test
    fun `concatenates token text in order`() {
        val rendered = listOf(
            BioToken.ArtistLink("Juana Molina", "Juana Molina", "https://www.discogs.com/search/?q=Juana+Molina&type=artist"),
            BioToken.PlainText(" released "),
            BioToken.Italic("DOGA"),
            BioToken.PlainText(" on "),
            BioToken.LabelName("Sonamos"),
        ).toBioAnnotatedString(linkColor)

        assertEquals("Juana Molina released DOGA on Sonamos", rendered.text)
    }

    @Test
    fun `styles bold italic and underline runs over exactly their text`() {
        val rendered = listOf(
            BioToken.Bold("Jessica Pratt"),
            BioToken.PlainText(" "),
            BioToken.Italic("On Your Own Love Again"),
            BioToken.PlainText(" "),
            BioToken.Underline("Drag City"),
        ).toBioAnnotatedString(linkColor)

        val styles = rendered.spanStyles.associate { rendered.text.substring(it.start, it.end) to it.item }
        assertEquals(FontWeight.Bold, styles.getValue("Jessica Pratt").fontWeight)
        assertEquals(FontStyle.Italic, styles.getValue("On Your Own Love Again").fontStyle)
        assertEquals(TextDecoration.Underline, styles.getValue("Drag City").textDecoration)
    }

    @Test
    fun `artist and url links become clickable url annotations`() {
        val rendered = listOf(
            BioToken.ArtistLink("Stereolab", "Stereolab", "https://www.discogs.com/search/?q=Stereolab&type=artist"),
            BioToken.PlainText(" "),
            BioToken.UrlLink("https://www.wxyc.org", "WXYC"),
        ).toBioAnnotatedString(linkColor)

        val links = rendered.getLinkAnnotations(0, rendered.length).map {
            rendered.text.substring(it.start, it.end) to (it.item as LinkAnnotation.Url).url
        }
        assertEquals(
            listOf(
                "Stereolab" to "https://www.discogs.com/search/?q=Stereolab&type=artist",
                "WXYC" to "https://www.wxyc.org",
            ),
            links,
        )
    }

    @Test
    fun `a url tag without a usable href is underlined text, not a link`() {
        val rendered = listOf(BioToken.UrlLink(null, "text")).toBioAnnotatedString(linkColor)

        assertTrue(rendered.getLinkAnnotations(0, rendered.length).isEmpty())
        assertEquals(TextDecoration.Underline, rendered.spanStyles.single().item.textDecoration)
    }

    @Test
    fun `prefers server tokens over parsing the raw bio`() {
        val raw = "[b]Stereolab[/b] formed in London."
        val served = listOf(BioToken.Bold("Stereolab"), BioToken.PlainText(" formed in London, 1990."))
        val cases = listOf(
            served to served,
            null to listOf(BioToken.Bold("Stereolab"), BioToken.PlainText(" formed in London.")),
            // Tokens with nothing to show fall back to the raw bio rather than hiding it.
            emptyList<BioToken>() to listOf(BioToken.Bold("Stereolab"), BioToken.PlainText(" formed in London.")),
            listOf(BioToken.PlainText("")) to listOf(BioToken.Bold("Stereolab"), BioToken.PlainText(" formed in London.")),
            listOf(BioToken.PlainText(" \n ")) to listOf(BioToken.Bold("Stereolab"), BioToken.PlainText(" formed in London.")),
        )
        for ((tokens, expected) in cases) {
            assertEquals("for $tokens", expected, bioTokensToShow(raw, tokens))
        }
    }
}
