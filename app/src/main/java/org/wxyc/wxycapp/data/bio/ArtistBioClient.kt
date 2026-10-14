package org.wxyc.wxycapp.data.bio

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.IOException

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
class ArtistBioClient(
    private val httpClient: OkHttpClient,
    private val baseUrl: String,
    clock: () -> Long = { System.currentTimeMillis() / 1000 },
) {

    /** Entity answers already fetched this process; Discogs names rarely change. */
    private val names = EntityNameCache(MAX_CACHED_NAMES, NEGATIVE_TTL_SECONDS, clock)

    /**
     * The best rendering the server can help with for [bio]. Server tokens win whenever
     * the server sent any. Only when it answered with none is [bio] parsed with its id
     * references resolved: up to [MAX_RESOLVED_PER_BIO] of them, [RESOLVE_CONCURRENCY] at a
     * time, stopping at the first failed call. A failed token lookup resolves nothing, so a
     * failing session costs one attempt per call, not one per id.
     */
    suspend fun renderedBio(artistName: String, releaseTitle: String?, bio: String): BioTokensResult =
        withContext(Dispatchers.IO) {
            when (val served = bioTokens(artistName, releaseTitle)) {
                BioTokensResult.NoTokens -> resolveReferences(bio)
                else -> served
            }
        }

    private suspend fun resolveReferences(bio: String): BioTokensResult {
        val resolved = mutableMapOf<EntityRef, String>()
        var failed = false
        val batches = DiscogsMarkupParser.entityReferences(bio).take(MAX_RESOLVED_PER_BIO).chunked(RESOLVE_CONCURRENCY)
        for (batch in batches) {
            val results = coroutineScope { batch.map { ref -> async { ref to runCatching { resolveEntity(ref) } } }.awaitAll() }
            results.forEach { (ref, result) -> result.getOrNull()?.let { resolved[ref] = it } }
            results.firstNotNullOfOrNull { it.second.exceptionOrNull() }?.let { e ->
                Log.w(TAG, "Stopped resolving bio ids: ${e.javaClass.simpleName} ${e.message.orEmpty()}")
                failed = true
            }
            if (failed) break
        }
        return when {
            resolved.isNotEmpty() -> BioTokensResult.Found(DiscogsMarkupParser.parse(bio, resolved), complete = !failed)
            failed -> BioTokensResult.Failed
            else -> BioTokensResult.NoTokens
        }
    }

    /**
     * The Discogs name for [ref] from `proxy/entity/resolve`, or null when the server says
     * there is none. Answers are cached. Blocking.
     *
     * @throws java.io.IOException when the call fails (auth, transport or a server error).
     */
    fun resolveEntity(ref: EntityRef): String? {
        names.get(ref)?.let { return it.name }
        val url = url("/proxy/entity/resolve")
            .addQueryParameter("type", ref.type.wireName)
            .addQueryParameter("id", ref.id.toString())
            .build()
        val name = get(url)?.get("name")?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
        names.put(ref, name)
        return name
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
        // Any token array is final, even one with no text: iOS DiscogsFormatter.resolvedBio.
        artist?.get("bioTokens")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { element -> element.takeIf { it.isJsonObject }?.let { decodeToken(it.asJsonObject) } }
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
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            runCatching { JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject }
                .getOrElse { throw IOException("Response is not a JSON object") }
        }

    companion object {
        private const val TAG = "ArtistBioClient"

        /** Bounds the calls one bio can cost against the proxy's 120 per minute per user. */
        private const val MAX_RESOLVED_PER_BIO = 20

        /** Resolutions in flight at once. */
        private const val RESOLVE_CONCURRENCY = 4

        private const val MAX_CACHED_NAMES = 500

        /** Matches Backend-Service's `ENTITY_RESOLVE_NEGATIVE_TTL_MS` (10 minutes). */
        private const val NEGATIVE_TTL_SECONDS = 600L

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
    /**
     * The server's parsed bio. [complete] is false when id resolution stopped partway at a
     * failure, so the caller should show it but try again later.
     */
    data class Found(val tokens: List<BioToken>, val complete: Boolean = true) : BioTokensResult

    /** The server answered and has no tokens for this artist. */
    object NoTokens : BioTokensResult

    /** A call failed; whether tokens exist is unknown. */
    object Failed : BioTokensResult
}
