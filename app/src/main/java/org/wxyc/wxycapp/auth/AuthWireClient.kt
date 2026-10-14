package org.wxyc.wxycapp.auth

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** The result of `POST /auth/sign-in/anonymous`. */
data class AnonymousSignIn(val sessionToken: String, val userId: String) {
    override fun toString(): String = "AnonymousSignIn(userId=$userId)"
}

/**
 * The two better-auth calls an anonymous listener session needs, mirroring iOS's
 * `DefaultAuthNetworkClient`: sign in anonymously, and mint a JWT from a session token.
 *
 * Both send `Origin`, `User-Agent` and, when one is available, the request line's
 * `X-Device-Fingerprint`, so a ban ties the session to the same install identity iOS uses.
 * Both are blocking; call them off the main thread. Failures are [java.io.IOException]s:
 * [AuthHttpException] for a non-success status, [AuthResponseException] for an unusable body.
 *
 * @param baseUrl the API origin with no trailing slash, e.g. `https://api.wxyc.org`.
 */
class AuthWireClient(
    private val httpClient: OkHttpClient,
    private val baseUrl: String,
    private val userAgent: String,
    private val fingerprint: () -> String?,
) {

    /** Creates a new anonymous user and returns its session. */
    fun signInAnonymously(): AnonymousSignIn {
        val request = request("/auth/sign-in/anonymous")
            .post("{}".toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        val body = call(request)
        val sessionToken = body.string("token")
        val userId = body["user"]?.takeIf { it.isJsonObject }?.asJsonObject?.string("id")
        if (sessionToken == null || userId == null) {
            throw AuthResponseException("Sign-in response is missing token or user id")
        }
        return AnonymousSignIn(sessionToken, userId)
    }

    /** Mints a short-lived JWT for [sessionToken]. */
    fun mintJwt(sessionToken: String): String {
        val request = request("/auth/token")
            .header("Authorization", "Bearer $sessionToken")
            .get()
            .build()
        return call(request).string("token")
            ?: throw AuthResponseException("Token response is missing token")
    }

    private fun request(path: String): Request.Builder = Request.Builder()
        .url(baseUrl + path)
        .header("Origin", baseUrl)
        .header("User-Agent", userAgent)
        .apply { fingerprint()?.let { header(FINGERPRINT_HEADER, it) } }

    private fun call(request: Request): JsonObject =
        httpClient.newCall(request).execute().use { response ->
            if (response.code != 200 && response.code != 201) throw AuthHttpException(response.code)
            runCatching { JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject }
                .getOrElse { throw AuthResponseException("Auth response is not a JSON object") }
        }

    private fun JsonObject.string(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotEmpty() }

    private companion object {
        const val FINGERPRINT_HEADER = "X-Device-Fingerprint"
    }
}
