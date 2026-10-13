package org.wxyc.wxycapp.data.bio

import java.net.URI
import java.net.URLEncoder
import java.util.Locale

/**
 * Parses the Discogs markup in an artist bio (a Discogs artist profile, passed through
 * unchanged by the backend) into [BioToken]s.
 *
 * A port of the iOS `DiscogsMarkupParser` sync API (wxyc-ios-64,
 * `Shared/Metadata/Sources/Metadata/DiscogsMarkupParser.swift`). Supported markup:
 * - `[a=Name]` artist link, `[l=Name]` label name
 * - `[b]`, `[i]`, `[u]` formatting and `[url=href]text[/url]`
 * - `[aNNNN]`, `[rNNNN]`/`[r=NNNN]`, `[mNNNN]`/`[m=NNNN]`, `[lNNNN]` id references
 *
 * Android has no entity resolver, so id references are dropped, and the whitespace and
 * punctuation around each drop is tidied so the sentence reads as though the reference had
 * never been there. Unknown and orphaned tags are skipped. In addition to iOS, HTML entities
 * are decoded and CRLF / CR line breaks become `\n`.
 */
object DiscogsMarkupParser {

    /** Parses [text] into display tokens. Never throws; malformed markup degrades to text. */
    fun parse(text: String): List<BioToken> {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        return normalizeAfterDrops(tokenize(normalized))
    }

    /** Removes a Discogs disambiguation suffix: "Salamanda (8)" becomes "Salamanda". */
    fun stripDisambiguationSuffix(name: String): String = name.replace(DISAMBIGUATION_SUFFIX, "")

    // Phase 1: tokenize. `null` marks an id reference that will be dropped.

    private fun tokenize(text: String): List<BioToken?> {
        val tokens = mutableListOf<BioToken?>()
        var pos = 0
        while (pos < text.length) {
            val open = text.indexOf('[', pos)
            if (open < 0) {
                tokens += plain(text.substring(pos))
                break
            }
            if (open > pos) tokens += plain(text.substring(pos, open))
            val close = text.indexOf(']', open)
            if (close < 0) {
                tokens += plain(text.substring(open))
                break
            }
            val tag = text.substring(open + 1, close)
            pos = close + 1
            when {
                ID_REFERENCE.matches(tag) -> tokens += null
                tag.startsWith("a=") && tag.length > 2 -> tokens += artistLink(decode(tag.substring(2)))
                tag.startsWith("l=") && tag.length > 2 -> tokens += BioToken.LabelName(decode(tag.substring(2)))
                tag.startsWith("url=") && tag.length > 4 -> {
                    val rawHref = tag.substring(4)
                    val closing = findClosingTag(text, pos, "url")
                    if (closing == null) {
                        tokens += plain(rawHref + text.substring(pos))
                        pos = text.length
                    } else {
                        tokens += BioToken.UrlLink(validHref(decode(rawHref)), decode(closing.first))
                        pos = closing.second
                    }
                }
                tag == "b" || tag == "i" || tag == "u" -> findClosingTag(text, pos, tag)?.let { (content, next) ->
                    val decoded = decode(content)
                    tokens += when (tag) {
                        "b" -> BioToken.Bold(decoded)
                        "i" -> BioToken.Italic(decoded)
                        else -> BioToken.Underline(decoded)
                    }
                    pos = next
                }
                // Unknown, empty, and orphaned closing tags are skipped.
            }
        }
        return tokens
    }

    /** Finds `[/tag]` from [start], tracking same-tag nesting. Returns (content, index after it). */
    private fun findClosingTag(text: String, start: Int, tag: String): Pair<String, Int>? {
        var depth = 1
        var search = start
        while (search < text.length) {
            val open = text.indexOf('[', search)
            if (open < 0) return null
            val close = text.indexOf(']', open)
            if (close < 0) return null
            when (text.substring(open + 1, close)) {
                tag -> depth++
                "/$tag" -> if (--depth == 0) return text.substring(start, open) to close + 1
            }
            search = close + 1
        }
        return null
    }

    private fun plain(raw: String) = BioToken.PlainText(decode(raw))

    private fun artistLink(name: String) = BioToken.ArtistLink(
        name = name,
        displayName = stripDisambiguationSuffix(name),
        url = "https://www.discogs.com/search/?q=${URLEncoder.encode(name, "UTF-8")}&type=artist",
    )

    /**
     * Returns [href] when it is an http(s) URL, else null. Spaces and pipes are
     * percent-encoded first, because `java.net.URI` rejects them where iOS `URL` accepts
     * them, and the scheme is lowercased, because Android matches intent schemes
     * case-sensitively and `HTTPS://` would otherwise find no browser.
     */
    private fun validHref(href: String): String? {
        val encoded = href.replace(" ", "%20").replace("|", "%7C")
        val scheme = runCatching { URI(encoded) }.getOrNull()?.scheme ?: return null
        val lowered = scheme.lowercase(Locale.ROOT).takeIf { it == "http" || it == "https" } ?: return null
        return lowered + encoded.substring(scheme.length)
    }

    // Phase 2: drop id references, tidying the text that sandwiched each one.

    private fun normalizeAfterDrops(tokens: List<BioToken?>): List<BioToken> {
        val result = mutableListOf<BioToken>()
        var pendingDrop = false
        for (token in tokens) {
            if (token == null) {
                pendingDrop = true
                continue
            }
            if (pendingDrop && token is BioToken.PlainText) {
                pendingDrop = false
                val previous = result.lastOrNull()
                when {
                    previous is BioToken.PlainText ->
                        result[result.lastIndex] = BioToken.PlainText(mergeAcrossDrop(previous.text, token.text))
                    previous == null -> trimLeadingOrphan(token.text).takeIf { it.isNotEmpty() }
                        ?.let { result += BioToken.PlainText(it) }
                    else -> result += BioToken.PlainText(cleanLeadingAfterDrop(token.text))
                }
                continue
            }
            pendingDrop = false
            result += token
        }
        val last = result.lastOrNull()
        if (pendingDrop && last is BioToken.PlainText) {
            result[result.lastIndex] = BioToken.PlainText(last.text.trimEnd())
        }
        return result
    }

    private fun mergeAcrossDrop(left: String, right: String): String {
        val spannedNewline = left.takeLastWhile(Char::isWhitespace).contains('\n') ||
            right.takeWhile(Char::isWhitespace).contains('\n')
        val leftTrimmed = left.trimEnd()
        var rightTrimmed = right.trimStart()
        val first = rightTrimmed.firstOrNull()
        if (first != null && first == leftTrimmed.lastOrNull() && first in CLOSING_PUNCTUATION) {
            rightTrimmed = rightTrimmed.drop(1).trimStart()
        }
        return when {
            rightTrimmed.firstOrNull()?.let { it in CLOSING_PUNCTUATION } == true -> leftTrimmed + rightTrimmed
            left.lastOrNull()?.isWhitespace() == true || right.firstOrNull()?.isWhitespace() == true ->
                leftTrimmed + (if (spannedNewline) "\n" else " ") + rightTrimmed
            else -> leftTrimmed + rightTrimmed
        }
    }

    private fun cleanLeadingAfterDrop(text: String): String {
        val trimmed = text.trimStart()
        return when {
            trimmed.firstOrNull()?.let { it in CLOSING_PUNCTUATION } == true -> trimmed
            text.firstOrNull()?.isWhitespace() == true -> " $trimmed"
            else -> text
        }
    }

    private fun trimLeadingOrphan(text: String): String {
        val trimmed = text.trimStart()
        return if (trimmed.firstOrNull()?.let { it in CLOSING_PUNCTUATION } == true) trimmed.drop(1).trimStart() else trimmed
    }

    // HTML entities. Decoded after tokenizing so an encoded "&#91;" never opens a tag.

    private fun decode(text: String): String = if ('&' !in text) text else ENTITY.replace(text) { match ->
        val body = match.groupValues[1]
        val codePoint = when {
            body.startsWith("#x", ignoreCase = true) -> body.drop(2).toIntOrNull(16)
            body.startsWith("#") -> body.drop(1).toIntOrNull()
            else -> NAMED_ENTITIES[body]
        }
        codePoint?.takeIf { Character.isValidCodePoint(it) }?.let { String(Character.toChars(it)) } ?: match.value
    }

    private val ID_REFERENCE = Regex("""a\d+|[rm]=?\d+|l\d+""")
    private val DISAMBIGUATION_SUFFIX = Regex(""" \(\d+\)$""")
    private val ENTITY = Regex("""&(#[xX][0-9a-fA-F]{1,6}|#\d{1,7}|[a-zA-Z]+);""")
    private val CLOSING_PUNCTUATION = setOf(',', '.', ';', ':', '!', '?', ')', ']', '}')
    private val NAMED_ENTITIES = mapOf(
        "amp" to '&'.code, "lt" to '<'.code, "gt" to '>'.code, "quot" to '"'.code, "apos" to '\''.code,
        "nbsp" to 0xA0, "ndash" to 0x2013, "mdash" to 0x2014, "hellip" to 0x2026,
        "lsquo" to 0x2018, "rsquo" to 0x2019, "ldquo" to 0x201C, "rdquo" to 0x201D,
    )
}
