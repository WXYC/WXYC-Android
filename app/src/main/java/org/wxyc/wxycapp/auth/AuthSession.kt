package org.wxyc.wxycapp.auth

import com.google.gson.JsonParser
import okio.ByteString.Companion.decodeBase64
import java.io.IOException

/**
 * An anonymous Backend-Service session: the long-lived better-auth [sessionToken], the
 * short-lived [jwt] minted from it, and the [jwt]'s `exp` claim.
 *
 * [toString] leaves the two tokens out, so a session that reaches a log line or an
 * exception message does not leak a credential.
 */
data class AuthSession(
    val sessionToken: String,
    val jwt: String,
    val userId: String,
    val jwtExpiresAtEpochSeconds: Long,
) {
    /** True when [jwt] expires within [marginSeconds] of [nowEpochSeconds]. */
    fun isJwtStale(nowEpochSeconds: Long, marginSeconds: Long): Boolean =
        nowEpochSeconds + marginSeconds >= jwtExpiresAtEpochSeconds

    override fun toString(): String =
        "AuthSession(userId=$userId, jwtExpiresAtEpochSeconds=$jwtExpiresAtEpochSeconds)"
}

/**
 * An auth endpoint answered with a status other than success. [retryAfterSeconds] is the
 * response's `Retry-After` when it gave one in seconds.
 */
class AuthHttpException(val statusCode: Int, val retryAfterSeconds: Long? = null) :
    IOException("Auth request failed with HTTP $statusCode")

/** A sign-in was skipped because an earlier one failed less than a cooldown ago. */
class AuthCooldownException : IOException("Anonymous sign-in is cooling down after a failure")

/** An auth endpoint answered with a body the client could not use. */
class AuthResponseException(message: String) : IOException(message)

/** Reads claims from a JWT payload without verifying it; the server verifies. */
object JwtClaims {
    /**
     * The `exp` claim of [jwt], in epoch seconds.
     *
     * @throws AuthResponseException when [jwt] is not three dot-separated parts or its
     * payload has no numeric `exp`.
     */
    fun expiry(jwt: String): Long {
        val payload = jwt.split('.').takeIf { it.size == 3 }?.get(1)
            ?: throw AuthResponseException("JWT does not have three parts")
        // Okio accepts the base64url alphabet; java.util.Base64 needs API 26 and minSdk is 24.
        val json = payload.decodeBase64()?.utf8()
            ?: throw AuthResponseException("JWT payload is not base64url")
        return runCatching { JsonParser.parseString(json).asJsonObject["exp"].asLong }
            .getOrElse { throw AuthResponseException("JWT payload has no numeric exp") }
    }
}
