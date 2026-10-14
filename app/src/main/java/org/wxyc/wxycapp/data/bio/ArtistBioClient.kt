package org.wxyc.wxycapp.data.bio

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

/**
 * Fetches the server's pre-parsed artist bio (`bioTokens`, LML's `profile_tokens`), which
 * iOS renders in preference to parsing the raw bio itself.
 *
 * The flowsheet carries no Discogs artist id, so this takes two calls, as iOS's
 * `PlaycutMetadataService` does: `/proxy/metadata/album` for `discogsArtistId`, then
 * `/proxy/metadata/artist` for the tokens. Both routes need the anonymous session's JWT, so
 * [httpClient] must be the `@Authenticated` client.
 *
 * @param baseUrl the API origin with no trailing slash, e.g. `https://api.wxyc.org`.
 */
class ArtistBioClient(private val httpClient: OkHttpClient, private val baseUrl: String) {

    /** Entity names already resolved this process; Discogs names rarely change. */
    private val names = ConcurrentHashMap<EntityRef, String>()

    /**
     * The best rendering the server can help with for [bio], or null to keep the local
     * parse: the server's [bioTokens] when there are any, otherwise [bio] parsed with its
     * id references resolved through [resolveEntity]. Blocking.
     */
    fun renderedBio(artistName: String, releaseTitle: String?, bio: String): List<BioToken>? {
        bioTokens(artistName, releaseTitle)?.let { return it }
        val resolved = DiscogsMarkupParser.entityReferences(bio).take(MAX_RESOLVED_PER_BIO)
            .mapNotNull { ref -> resolveEntity(ref)?.let { ref to it } }
            .toMap()
        return resolved.takeIf { it.isNotEmpty() }?.let { DiscogsMarkupParser.parse(bio, it) }
    }

    /** The Discogs name for [ref] from `proxy/entity/resolve`, or null when it cannot be had. Blocking. */
    fun resolveEntity(ref: EntityRef): String? = names[ref] ?: try {
        val url = url("/proxy/entity/resolve")
            .addQueryParameter("type", ref.type.wireName)
            .addQueryParameter("id", ref.id.toString())
            .build()
        get(url)["name"]?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
            ?.also { names[ref] = it }
    } catch (e: Exception) {
        Log.w(TAG, "Entity ${ref.type.wireName} unresolved: ${e.javaClass.simpleName} ${e.message.orEmpty()}")
        null
    }

    /**
     * The bio tokens for the artist of [artistName]'s [releaseTitle]. [BioTokensResult.NoTokens]
     * means the server answered and has none to give (no Discogs artist, a 404 from either
     * route, or no tokens); [BioTokensResult.Failed] means a call failed and nothing is known. Blocking.
     */
    fun bioTokens(artistName: String, releaseTitle: String?): BioTokensResult = try {
        val albumUrl = url("/proxy/metadata/album").addQueryParameter("artistName", artistName)
            .apply { releaseTitle?.let { addQueryParameter("releaseTitle", it) } }.build()
        val artistId = get(albumUrl)?.number("discogsArtistId")
        val artist = artistId?.let { get(url("/proxy/metadata/artist").addQueryParameter("artistId", it.toString()).build()) }
        artist?.get("bioTokens")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { element -> element.takeIf { it.isJsonObject }?.let { decodeToken(it.asJsonObject) } }
            ?.takeIf { tokens -> tokens.any { it.text.isNotEmpty() } }
            ?.let { BioTokensResult.Found(it) }
            ?: BioTokensResult.NoTokens
    } catch (e: Exception) {
        // Status and class name only: the request carried a bearer JWT.
        Log.w(TAG, "Artist bio tokens unavailable: ${e.javaClass.simpleName} ${e.message.orEmpty()}")
        BioTokensResult.Failed
    }

    private fun url(path: String): HttpUrl.Builder = "$baseUrl$path".toHttpUrl().newBuilder()

    /** The JSON object at [url], or null for a 404 (a confirmed absence); throws on any other failure. */
    private fun get(url: HttpUrl): JsonObject? =
        httpClient.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            if (response.code == 404) return null
            check(response.isSuccessful) { "HTTP ${response.code}" }
            JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject
        }

    companion object {
        private const val TAG = "ArtistBioClient"

        /** Bounds the calls one bio can cost against the proxy's 120 per minute per user. */
        private const val MAX_RESOLVED_PER_BIO = 20

        /**
         * Maps one `DiscogsResolvedToken` to a [BioToken] with iOS `ResolvedBioToken`'s rules:
         * an unknown type is dropped (null), and a token missing a field its type needs
         * becomes plain text of its best available text. Release and master links render
         * like any other link, so they become [BioToken.UrlLink]s. A link that is not
         * http(s) keeps its text and loses the link, as the markup parser does.
         */
        fun decodeToken(json: JsonObject): BioToken? {
            fun field(key: String) = json[key]?.takeIf { it.isJsonPrimitive }?.asString
            val best = listOf("display_name", "title", "content", "text", "name").firstNotNullOfOrNull(::field).orEmpty()
            val content = field("content")
            val link = field("url")?.let(::httpUrlOrNull)
            return when (field("type")) {
                "plainText" -> BioToken.PlainText((field("text") ?: best).replace("\r\n", "\n"))
                "artistLink" -> {
                    val name = field("name")
                    val displayName = field("display_name")
                    if (name != null && displayName != null && link != null) BioToken.ArtistLink(name, displayName, link)
                    else BioToken.PlainText(best)
                }
                "labelName" -> field("name")?.let { BioToken.LabelName(it) } ?: BioToken.PlainText(best)
                "releaseLink", "masterLink" -> field("title")?.takeIf { link != null }
                    ?.let { BioToken.UrlLink(link, it) } ?: BioToken.PlainText(best)
                "bold" -> content?.let { BioToken.Bold(it) } ?: BioToken.PlainText(best)
                "italic" -> content?.let { BioToken.Italic(it) } ?: BioToken.PlainText(best)
                "underline" -> content?.let { BioToken.Underline(it) } ?: BioToken.PlainText(best)
                "urlLink" -> content?.let { BioToken.UrlLink(field("href")?.let(::httpUrlOrNull), it) }
                    ?: BioToken.PlainText(best)
                else -> null
            }
        }

        private fun httpUrlOrNull(value: String): String? =
            value.takeIf { it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true) }

        private fun JsonObject.number(key: String): Long? =
            get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong
    }
}

/** What [ArtistBioClient.bioTokens] learned. */
sealed interface BioTokensResult {
    /** The server's parsed bio. */
    data class Found(val tokens: List<BioToken>) : BioTokensResult

    /** The server answered and has no tokens for this artist. */
    object NoTokens : BioTokensResult

    /** A call failed; whether tokens exist is unknown. */
    object Failed : BioTokensResult
}
