package org.wxyc.wxycapp.auth

import android.util.Log
import java.io.IOException

/** Persists the anonymous session across launches. */
interface SessionStore {
    /** The stored session, or null when there is none or it cannot be read. Never throws. */
    fun load(): AuthSession?

    /** Stores [session], replacing any previous one. May throw; callers tolerate failure. */
    fun save(session: AuthSession)

    /** Removes the stored session. */
    fun clear()
}

/**
 * Owns the app's anonymous Backend-Service session and hands out bearer JWTs, following
 * iOS's `AuthenticationService`:
 *
 * 1. a JWT more than [FRESHNESS_MARGIN_SECONDS] from expiry, from memory or [store], is used as is;
 * 2. otherwise a new JWT is minted from the stored session token, keeping the same anonymous user;
 * 3. if the mint answers 401 or 404 (the session expired or was deleted), the stored session is
 *    cleared and the app signs in anonymously again.
 *
 * A failed sign-in blocks further sign-ins for [SIGN_IN_COOLDOWN_SECONDS], or for the
 * server's `Retry-After` when that is longer; calls in the meantime throw
 * [AuthCooldownException] without touching the network. Sign-in shares Backend-Service's
 * per-IP bucket (10 per 15 minutes) with DJ password sign-in, so a listener retrying on
 * every request could lock DJs on the same network out. A session whose first mint fails is
 * kept and re-minted later rather than orphaned.
 *
 * Any other failure propagates and the next call starts over. Both entry points are
 * blocking and synchronized, so concurrent callers queue behind one refresh and then find
 * its result in memory; call them off the main thread.
 *
 * @param clock the current time in epoch seconds.
 */
class AnonymousAuthenticator(
    private val wire: AuthWireClient,
    private val store: SessionStore,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    private var cached: AuthSession? = null

    /** Epoch second before which no sign-in is attempted. */
    private var signInBlockedUntil = 0L

    /** A JWT to send as `Authorization: Bearer`, refreshing the session if needed. */
    @Synchronized
    fun token(): String {
        cached?.takeUnless { it.isStale() }?.let { return it.jwt }
        return refresh(trustStoredJwt = true)
    }

    /**
     * A replacement for [rejectedJwt], which the server just answered 401 to.
     *
     * Returns the in-memory JWT when another caller already replaced [rejectedJwt].
     * Otherwise it mints a new one without trusting the stored JWT, which can look fresh
     * on this device and still be rejected.
     */
    @Synchronized
    fun reauthenticate(rejectedJwt: String): String {
        cached?.takeIf { it.jwt != rejectedJwt && !it.isStale() }?.let { return it.jwt }
        return refresh(trustStoredJwt = false)
    }

    private fun refresh(trustStoredJwt: Boolean): String {
        // The in-memory copy is the fallback when storage never persisted the session.
        val loaded = store.load() ?: cached
        if (trustStoredJwt && loaded != null && !loaded.isStale()) {
            cached = loaded
            return loaded.jwt
        }
        if (loaded != null) {
            try {
                return remember(loaded.sessionToken, loaded.userId, wire.mintJwt(loaded.sessionToken))
            } catch (e: AuthHttpException) {
                if (e.statusCode != 401 && e.statusCode != 404) throw e
                cached = null
                store.clear()
            }
        }
        val signIn = signInUnlessCoolingDown()
        // Keep the session before minting: if the mint fails, the next refresh mints from it
        // instead of creating another anonymous user. An empty JWT that expired at the epoch
        // is always stale.
        keep(AuthSession(signIn.sessionToken, jwt = "", userId = signIn.userId, jwtExpiresAtEpochSeconds = 0))
        return remember(signIn.sessionToken, signIn.userId, wire.mintJwt(signIn.sessionToken))
    }

    private fun signInUnlessCoolingDown(): AnonymousSignIn {
        val now = clock()
        if (now < signInBlockedUntil) throw AuthCooldownException()
        return try {
            wire.signInAnonymously()
        } catch (e: IOException) {
            val retryAfter = (e as? AuthHttpException)?.retryAfterSeconds ?: 0
            signInBlockedUntil = now + maxOf(SIGN_IN_COOLDOWN_SECONDS, retryAfter)
            throw e
        }
    }

    /** Keeps the session in memory and, best effort, in [store]; returns [jwt]. */
    private fun remember(sessionToken: String, userId: String, jwt: String): String {
        keep(AuthSession(sessionToken, jwt, userId, JwtClaims.expiry(jwt)))
        return jwt
    }

    private fun keep(session: AuthSession) {
        cached = session
        try {
            store.save(session)
        } catch (e: Exception) {
            // The in-memory session still works; only the class name is logged, never a token.
            Log.w(TAG, "Could not persist the auth session: ${e.javaClass.simpleName}")
        }
    }

    private fun AuthSession.isStale() = isJwtStale(clock(), FRESHNESS_MARGIN_SECONDS)

    private companion object {
        /** Matches iOS: 60 s of margin against a JWT that lives at least 15 minutes. */
        const val FRESHNESS_MARGIN_SECONDS = 60L

        /** How long a failed sign-in blocks the next one; a third of the shared per-IP budget. */
        const val SIGN_IN_COOLDOWN_SECONDS = 300L
        const val TAG = "AnonymousAuthenticator"
    }
}
