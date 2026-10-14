package org.wxyc.wxycapp.auth

import okio.ByteString.Companion.encodeUtf8
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Builds an unsigned JWT-shaped string whose payload carries [exp], for tests only. */
internal fun fakeJwt(exp: Long, subject: String = "anon-user"): String {
    val header = """{"alg":"EdDSA"}""".encodeUtf8().base64Url().trimEnd('=')
    val payload = """{"sub":"$subject","exp":$exp}""".encodeUtf8().base64Url().trimEnd('=')
    return "$header.$payload.signature"
}

class AuthSessionTest {

    @Test
    fun `reads the expiry from the exp claim`() {
        assertEquals(1_800_000_000L, JwtClaims.expiry(fakeJwt(exp = 1_800_000_000L)))
    }

    @Test
    fun `rejects strings that are not a JWT with an exp claim`() {
        val malformed = listOf(
            "",
            "not-a-jwt",
            "a.%%%.c",
            "a.${"""{"sub":"x"}""".encodeUtf8().base64Url()}.c",
            "a.${"""{"exp":"soon"}""".encodeUtf8().base64Url()}.c",
        )
        for (jwt in malformed) {
            assertThrows("for '$jwt'", AuthResponseException::class.java) { JwtClaims.expiry(jwt) }
        }
    }

    @Test
    fun `a JWT is stale within the margin of its expiry`() {
        val session = session(jwtExpiresAt = 1_000L)
        val cases = mapOf(
            0L to false,
            939L to false,
            940L to true,
            1_000L to true,
            2_000L to true,
        )
        for ((now, stale) in cases) {
            assertEquals("at $now", stale, session.isJwtStale(nowEpochSeconds = now, marginSeconds = 60))
        }
    }

    @Test
    fun `toString never prints the tokens`() {
        val text = session(jwtExpiresAt = 1_000L).toString()
        assertFalse(text.contains("secret-session"))
        assertFalse(text.contains("secret-jwt"))
        assertTrue(text.contains("user-1"))
    }

    private fun session(jwtExpiresAt: Long) = AuthSession(
        sessionToken = "secret-session",
        jwt = "secret-jwt",
        userId = "user-1",
        jwtExpiresAtEpochSeconds = jwtExpiresAt,
    )
}
