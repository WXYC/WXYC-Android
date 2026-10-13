package org.wxyc.wxycapp.data.bio

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Text output of [DiscogsMarkupParser.parse], ported case-for-case from the iOS
 * `DiscogsMarkupParserTests` sync suites (wxyc-ios-64,
 * Shared/Metadata/Tests/MetadataTests/DiscogsMarkupParserTests.swift), plus the
 * Android-only cases from WXYC-Android#74: HTML entities, CRLF line breaks, and
 * `[lNNNN]` label ids. Android has no entity resolver, so every id-based tag drops,
 * which is the iOS sync-API behavior.
 */
@RunWith(Parameterized::class)
class DiscogsMarkupParserTextTest(
    @Suppress("unused") private val name: String,
    private val input: String,
    private val expected: String,
) {
    @Test
    fun `renders the expected text`() {
        assertEquals(expected, DiscogsMarkupParser.parse(input).plainText())
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<String>> = listOf(
            // Artist tags
            arrayOf("artist name link", "[a=Juana Molina]", "Juana Molina"),
            arrayOf("artist name with apostrophe", "[a=Guns N' Roses]", "Guns N' Roses"),
            arrayOf("artist name strips disambiguation", "[a=Salamanda (8)]", "Salamanda"),
            arrayOf("artist id drops", "[a12345]", ""),
            arrayOf("large artist id drops", "[a9999999]", ""),
            arrayOf("artist id between words", "See [a12345] for more", "See for more"),
            // Bold / italic / underline
            arrayOf("bold", "[b]bold text[/b]", "bold text"),
            arrayOf("bold with surrounding text", "This is [b]bold[/b] text", "This is bold text"),
            arrayOf("orphaned bold close", "text [/b] more", "text  more"),
            arrayOf("unclosed bold", "[b]no closing tag", "no closing tag"),
            arrayOf("empty bold", "[b][/b]", ""),
            arrayOf("italic", "[i]italic text[/i]", "italic text"),
            arrayOf("italic with surrounding text", "This is [i]italic[/i] text", "This is italic text"),
            arrayOf("orphaned italic close", "text [/i] more", "text  more"),
            arrayOf("unclosed italic", "[i]no closing tag", "no closing tag"),
            arrayOf("underline", "[u]underlined text[/u]", "underlined text"),
            arrayOf("underline with surrounding text", "This is [u]underlined[/u] text", "This is underlined text"),
            arrayOf("orphaned underline close", "text [/u] more", "text  more"),
            arrayOf("unclosed underline", "[u]no closing tag", "no closing tag"),
            // Labels
            arrayOf("label name", "[l=Drag City]", "Drag City"),
            arrayOf("label name with digits", "[l=4AD]", "4AD"),
            arrayOf("label name in sentence", "Released on [l=Sonamos] in 2013", "Released on Sonamos in 2013"),
            arrayOf("label id drops", "Released on [l12345] in 2013", "Released on in 2013"),
            // URLs
            arrayOf("url link", "[url=https://example.com]Click here[/url]", "Click here"),
            arrayOf("url without close shows url", "[url=https://example.com]orphaned", "https://example.comorphaned"),
            arrayOf("invalid url keeps text", "[url=not a valid url]text[/url]", "text"),
            arrayOf("url with query string", "[url=https://example.com/path?query=value&other=123]Link[/url]", "Link"),
            // Release / master ids
            arrayOf("release id drops", "[r12345]", ""),
            arrayOf("master id drops", "[m123]", ""),
            arrayOf("release id with equals drops", "[r=621811]", ""),
            arrayOf("master id with equals drops", "[m=199386]", ""),
            arrayOf("release id between words", "See release [r99999] for details", "See release for details"),
            arrayOf("master id between words", "Master [m456] version", "Master version"),
            // Edge cases
            arrayOf("plain text", "Just plain text with no formatting", "Just plain text with no formatting"),
            arrayOf("empty string", "", ""),
            arrayOf("consecutive tags", "[a=Artist A][a=Artist B][a=Artist C]", "Artist AArtist BArtist C"),
            arrayOf("mixed tags and text", "Check out [a=Stereolab] on [l=Duophonic]!", "Check out Stereolab on Duophonic!"),
            arrayOf("nested different tags stay raw", "[b]bold [i]and italic[/i][/b]", "bold [i]and italic[/i]"),
            arrayOf("unclosed bracket", "Text with [unclosed bracket", "Text with [unclosed bracket"),
            arrayOf("unknown tags", "[unknown]text[/unknown]", "text"),
            arrayOf(
                "real-world text",
                "Written by [a=Jessica Pratt] and [a=Juana Molina]. Released on [l=Drag City] in 2015. See [r123456] for more info.",
                "Written by Jessica Pratt and Juana Molina. Released on Drag City in 2015. See for more info.",
            ),
            arrayOf("only brackets", "[]", ""),
            arrayOf("nested same-type tags", "[b]outer [b]inner[/b] outer[/b]", "outer [b]inner[/b] outer"),
            arrayOf("formatting in sequence", "[b]bold[/b] then [i]italic[/i] then [u]underline[/u]", "bold then italic then underline"),
            // Drop normalization
            arrayOf(
                "closing punctuation abuts drop",
                "Member of the electronica duo [a87717], formed with his sibling [a325359].",
                "Member of the electronica duo, formed with his sibling.",
            ),
            arrayOf("spaced punctuation after drop", "duo [a87717] , formed", "duo, formed"),
            arrayOf("double space collapses", "with [a1] and", "with and"),
            arrayOf("drop at start left-trims", "[a1] leads the sentence", "leads the sentence"),
            arrayOf("drop at end right-trims", "the sentence ends with [a1]", "the sentence ends with"),
            arrayOf(
                "no id references untouched",
                "Written by [a=Juana Molina] and [a=Stereolab]. Released on [l=Drag City] in 2013.",
                "Written by Juana Molina and Stereolab. Released on Drag City in 2013.",
            ),
            arrayOf("duplicated separator collapses", "[a=Stereolab], [a1], [a=Cat Power]", "Stereolab, Cat Power"),
            arrayOf("run of drops does not accumulate", "Members: [a1], [a2], [a3].", "Members:,."),
            arrayOf("closing paren hugs", "band (aka [a1]) toured", "band (aka) toured"),
            arrayOf("punctuation hugs preceding link", "[a=Cat Power][a1] , solo work", "Cat Power, solo work"),
            arrayOf("leading orphan punctuation drops", "[a1], and her sibling formed the band", "and her sibling formed the band"),
            arrayOf("paragraph break survives drop", "end of paragraph [a1]\n\nNext paragraph", "end of paragraph\nNext paragraph"),
            arrayOf(
                "issue example: id link",
                "American rap group founded by [a56678] and DJ Headliner.",
                "American rap group founded by and DJ Headliner.",
            ),
            // HTML entities (WXYC-Android#74)
            arrayOf("ampersand entity", "Rapper &amp; producer from Durham", "Rapper & producer from Durham"),
            arrayOf("quote and angle entities", "&quot;Doga&quot; &lt;2008&gt;", "\"Doga\" <2008>"),
            arrayOf("apostrophe entities", "Pratt&#39;s &apos;On Your Own&apos;", "Pratt's 'On Your Own'"),
            arrayOf("hex numeric entity", "Csillagrabl&#xF3;k", "Csillagrablók"),
            arrayOf("decimal numeric entity", "Nil&#252;fer Yanya", "Nilüfer Yanya"),
            arrayOf("unknown entity stays", "AT&T &bogus; ok", "AT&T &bogus; ok"),
            arrayOf("entity inside bold", "[b]Sonamos &amp; Domino[/b]", "Sonamos & Domino"),
            arrayOf("entity inside name link", "[a=Duke Ellington &amp; John Coltrane]", "Duke Ellington & John Coltrane"),
            arrayOf("encoded bracket is not a tag", "&#91;b&#93; literal", "[b] literal"),
            // CRLF (WXYC-Android#74)
            arrayOf("crlf paragraphs", "Line one. \r\n\r\nLine two.", "Line one. \n\nLine two."),
            arrayOf("lone cr", "Line one.\rLine two.", "Line one.\nLine two."),
            arrayOf("crlf around a drop", "Formed by [a1]\r\n\r\nBorn in Buenos Aires", "Formed by\nBorn in Buenos Aires"),
        )
    }
}

/** Token-shape assertions: which spans carry formatting and links. */
class DiscogsMarkupParserTokenTest {

    @Test
    fun `bold italic and underline produce styled tokens`() {
        assertEquals(
            listOf(BioToken.Bold("b"), BioToken.PlainText(" "), BioToken.Italic("i"), BioToken.PlainText(" "), BioToken.Underline("u")),
            DiscogsMarkupParser.parse("[b]b[/b] [i]i[/i] [u]u[/u]"),
        )
    }

    @Test
    fun `artist name link points at a Discogs artist search and keeps the raw name`() {
        assertEquals(
            listOf(
                BioToken.ArtistLink(
                    name = "Chuquimamani-Condori (2)",
                    displayName = "Chuquimamani-Condori",
                    url = "https://www.discogs.com/search/?q=Chuquimamani-Condori+%282%29&type=artist",
                ),
            ),
            DiscogsMarkupParser.parse("[a=Chuquimamani-Condori (2)]"),
        )
    }

    @Test
    fun `url tag keeps its href`() {
        assertEquals(
            listOf(BioToken.UrlLink("https://example.com/path?query=value&other=123", "Link")),
            DiscogsMarkupParser.parse("[url=https://example.com/path?query=value&amp;other=123]Link[/url]"),
        )
    }

    @Test
    fun `url tag with an unparseable href keeps the text but no href`() {
        assertEquals(listOf(BioToken.UrlLink(null, "text")), DiscogsMarkupParser.parse("[url=not a valid url]text[/url]"))
    }

    @Test
    fun `label name is unlinked`() {
        assertEquals(listOf(BioToken.LabelName("Drag City")), DiscogsMarkupParser.parse("[l=Drag City]"))
    }

    @Test
    fun `plain text is a single plain token`() {
        assertEquals(listOf(BioToken.PlainText("plain text")), DiscogsMarkupParser.parse("plain text"))
    }
}

class StripDisambiguationSuffixTest {

    @Test
    fun `removes a numeric suffix and keeps everything else`() {
        mapOf(
            "Artist (2)" to "Artist",
            "Artist (123)" to "Artist",
            "Artist (Band)" to "Artist (Band)",
            "Level 42" to "Level 42",
            "Artist" to "Artist",
        ).forEach { (input, expected) ->
            assertEquals(input, expected, DiscogsMarkupParser.stripDisambiguationSuffix(input))
        }
    }
}

/** Concatenates the visible text of every token, the way the renderer lays it out. */
internal fun List<BioToken>.plainText(): String = joinToString("") { it.text }
