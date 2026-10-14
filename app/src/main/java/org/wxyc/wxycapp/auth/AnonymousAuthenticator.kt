package org.wxyc.wxycapp.auth

import android.os.SystemClock
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
 * A failed sign-in, or a new session whose first mint is rejected with 401 or 404, blocks
 * further sign-ins for the server's `Retry-After` clamped to [MIN_COOLDOWN_SECONDS] ..
 * [MAX_COOLDOWN_SECONDS] (the minimum when it sends none). Calls in the meantime throw
 * [AuthCooldownException] without touching the network. Sign-in shares Backend-Service's
 * per-IP bucket (10 per 15 minutes) with DJ password sign-in, so a listener retrying on
 * every request could lock DJs on the same network out. A session whose first mint fails
 * for any other reason is kept and re-minted later rather than orphaned.
 *
 * Any other failure propagates and the next call starts over. Both entry points are
 * blocking and synchronized, so concurrent callers queue behind one refresh and then find
 * its result in memory; call them off the main thread.
 *
 * @param clock the current time in epoch seconds, compared with JWT `exp` claims.
 * @param monotonicClock seconds on a clock that never jumps (time since boot); times the
 *   cooldown, so a wall-clock correction can neither stretch nor cut it short.
 */
class AnonymousAuthenticator(
    private val wire: AuthWireClient,
    private val store: SessionStore,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000 },
    private val monotonicClock: () -> Long = { SystemClock.elapsedRealtime() / 1000 },
) : BearerTokenProvider {
    private var cached: AuthSession? = null

    /** [monotonicClock] second before which no sign-in is attempted; null when not cooling down. */
    private var signInBlockedUntil: Long? = null

    /** A JWT to send as `Authorization: Bearer`, refreshing the session if needed. */
    @Synchronized
    override fun token(): String {
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
    override fun reauthenticate(rejectedJwt: String): String {
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
        return try {
            remember(signIn.sessionToken, signIn.userId, wire.mintJwt(signIn.sessionToken))
        } catch (e: AuthHttpException) {
            // A session rejected on its very first mint is useless, and signing in again
            // would only be rejected the same way: drop it and cool down.
            if (e.statusCode == 401 || e.statusCode == 404) {
                cached = null
                store.clear()
                startCooldown(e.retryAfterSeconds)
            }
            throw e
        }
    }

    private fun signInUnlessCoolingDown(): AnonymousSignIn {
        signInBlockedUntil?.let { if (monotonicClock() < it) throw AuthCooldownException() }
        return try {
            wire.signInAnonymously()
        } catch (e: IOException) {
            startCooldown((e as? AuthHttpException)?.retryAfterSeconds)
            throw e
        }
    }

    /** Blocks sign-in for [retryAfterSeconds] clamped to the cooldown bounds. Clamping first keeps the sum from overflowing. */
    private fun startCooldown(retryAfterSeconds: Long?) {
        val seconds = (retryAfterSeconds ?: MIN_COOLDOWN_SECONDS).coerceIn(MIN_COOLDOWN_SECONDS, MAX_COOLDOWN_SECONDS)
        signInBlockedUntil = monotonicClock() + seconds
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

        /** Shortest sign-in cooldown: at most 3 attempts per 15-minute window of the shared per-IP budget. */
        const val MIN_COOLDOWN_SECONDS = 300L

        /** Longest sign-in cooldown, whatever `Retry-After` says. */
        const val MAX_COOLDOWN_SECONDS = 3_600L
        const val TAG = "AnonymousAuthenticator"
    }
}
