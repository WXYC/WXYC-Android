package org.wxyc.wxycapp.data.bio

/**
 * One rendered run of an artist bio, after its Discogs markup is parsed.
 *
 * Mirrors the iOS `ResolvedBioToken` / `DiscogsMarkupParser.ResolvedToken` cases that a bio
 * can produce without an entity resolver. [text] is what the run shows on screen; the
 * subtype says how it is styled or linked.
 */
sealed class BioToken {
    /** The visible text of this run. */
    abstract val text: String

    /** Unformatted text. */
    data class PlainText(override val text: String) : BioToken()

    /**
     * An `[a=Name]` artist reference. [name] is the raw Discogs name, [displayName] the same
     * name with any " (N)" disambiguation suffix removed, and [url] a Discogs artist search.
     */
    data class ArtistLink(val name: String, val displayName: String, val url: String) : BioToken() {
        override val text: String get() = displayName
    }

    /** An `[l=Name]` label reference, shown as plain text. */
    data class LabelName(val name: String) : BioToken() {
        override val text: String get() = name
    }

    /** `[b]...[/b]`; the content is not parsed further, matching Discogs. */
    data class Bold(override val text: String) : BioToken()

    /** `[i]...[/i]`; the content is not parsed further, matching Discogs. */
    data class Italic(override val text: String) : BioToken()

    /** `[u]...[/u]`; the content is not parsed further, matching Discogs. */
    data class Underline(override val text: String) : BioToken()

    /** `[url=href]content[/url]`. [href] is null when it is not an http(s) URL. */
    data class UrlLink(val href: String?, val content: String) : BioToken() {
        override val text: String get() = content
    }
}
