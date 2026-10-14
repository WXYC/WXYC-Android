package org.wxyc.wxycapp.auth

import android.util.Log

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
        val signIn = wire.signInAnonymously()
        return remember(signIn.sessionToken, signIn.userId, wire.mintJwt(signIn.sessionToken))
    }

    /** Keeps the session in memory and, best effort, in [store]; returns [jwt]. */
    private fun remember(sessionToken: String, userId: String, jwt: String): String {
        val updated = AuthSession(sessionToken, jwt, userId, JwtClaims.expiry(jwt))
        cached = updated
        try {
            store.save(updated)
        } catch (e: Exception) {
            // The in-memory session still works; only the class name is logged, never a token.
            Log.w(TAG, "Could not persist the auth session: ${e.javaClass.simpleName}")
        }
        return jwt
    }

    private fun AuthSession.isStale() = isJwtStale(clock(), FRESHNESS_MARGIN_SECONDS)

    private companion object {
        /** Matches iOS: 60 s of margin against a JWT that lives at least 15 minutes. */
        const val FRESHNESS_MARGIN_SECONDS = 60L
        const val TAG = "AnonymousAuthenticator"
    }
}
