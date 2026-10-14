package org.wxyc.wxycapp.auth

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/** Supplies bearer JWTs; [AnonymousAuthenticator] is the production implementation. */
interface BearerTokenProvider {
    /** A JWT to send now. Blocking; may throw [java.io.IOException]. */
    fun token(): String

    /** A replacement for [rejectedJwt] after the server answered 401 to it. */
    fun reauthenticate(rejectedJwt: String): String
}

/**
 * Adds `Authorization: Bearer <jwt>` to every request and, when the response is 401,
 * reauthenticates and retries exactly once, like iOS's `URLSession.authedData`.
 *
 * Only 401 is retried. A 403 means the JWT was valid and refused (a banned session), and
 * a second 401 is returned as is so a broken session cannot loop. Install it with
 * [okhttp3.OkHttpClient.Builder.addInterceptor] (an application interceptor), which may
 * call [Interceptor.Chain.proceed] twice.
 *
 * Requests to any host other than [apiHost] (a redirect, or a client reused for another
 * service) pass through untouched, so the JWT never leaves the API.
 */
class AuthInterceptor(private val tokens: BearerTokenProvider, private val apiHost: String) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        if (!chain.request().url.host.equals(apiHost, ignoreCase = true)) return chain.proceed(chain.request())
        val jwt = tokens.token()
        val response = chain.proceed(chain.request().withBearer(jwt))
        if (response.code != 401) return response
        response.close()
        return chain.proceed(chain.request().withBearer(tokens.reauthenticate(jwt)))
    }

    private fun Request.withBearer(jwt: String): Request =
        newBuilder().header("Authorization", "Bearer $jwt").build()
}
