package org.wxyc.wxycapp.data.bio

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

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

    /**
     * The bio tokens for the artist of [artistName]'s [releaseTitle], or null when there are
     * none or anything fails; the caller then keeps parsing the raw bio. Blocking.
     */
    fun bioTokens(artistName: String, releaseTitle: String?): List<BioToken>? = try {
        val album = get(url("/proxy/metadata/album").addQueryParameter("artistName", artistName)
            .apply { releaseTitle?.let { addQueryParameter("releaseTitle", it) } }.build())
        album.number("discogsArtistId")?.let { artistId ->
            val artist = get(url("/proxy/metadata/artist").addQueryParameter("artistId", artistId.toString()).build())
            artist["bioTokens"]?.takeIf { it.isJsonArray }?.asJsonArray
                ?.mapNotNull { element -> element.takeIf { it.isJsonObject }?.let { decodeToken(it.asJsonObject) } }
                ?.takeIf { tokens -> tokens.any { it.text.isNotEmpty() } }
        }
    } catch (e: Exception) {
        // Status and class name only: the request carried a bearer JWT.
        Log.w(TAG, "Artist bio tokens unavailable: ${e.javaClass.simpleName} ${e.message.orEmpty()}")
        null
    }

    private fun url(path: String): HttpUrl.Builder = "$baseUrl$path".toHttpUrl().newBuilder()

    private fun get(url: HttpUrl): JsonObject =
        httpClient.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject
        }

    companion object {
        private const val TAG = "ArtistBioClient"

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
