package org.wxyc.wxycapp.requestline

import android.util.Log
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.wxyc.wxycapp.analytics.AnalyticsEvents

/**
 * Sends listener requests to request-o-matic, the same service the iOS app uses.
 *
 * Request-o-matic parses the message, looks it up in the library, posts it to
 * Slack, and enforces request-line bans keyed on [fingerprint]. The previous
 * implementation posted straight to a Slack webhook whose key came from a
 * Railway service that has since been deleted, so requests never landed.
 *
 * [send] is blocking; call it off the main thread.
 */
class RequestLineClient(
    private val httpClient: OkHttpClient,
    private val endpoint: String,
    private val userAgent: String,
    private val fingerprint: () -> String?,
    private val capture: (String, Map<String, Any>) -> Unit,
) {

    sealed interface Result {
        object Sent : Result
        data class Failed(val statusCode: Int) : Result
        data class NetworkError(val cause: Throwable) : Result
    }

    fun send(message: String): Result {
        val startedNanos = System.nanoTime()

        return try {
            val body = JsonObject().apply { addProperty("message", message) }.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            // Inside the guard: the fingerprint lambda reads storage, and a
            // failure there must not escape send() either.
            val request = Request.Builder()
                .url(endpoint)
                .header("User-Agent", userAgent)
                .apply { fingerprint()?.let { header(FINGERPRINT_HEADER, it) } }
                .post(body)
                .build()

            httpClient.newCall(request).execute().use { response ->
                // Peek, don't consume: string() on the live body can throw
                // mid-read (headers arrive, connection drops), which would
                // turn a request with a known status into a network error and
                // lose the status from both the UI and the logs.
                val errorBody = if (response.isSuccessful) {
                    null
                } else {
                    runCatching { response.peekBody(ERROR_BODY_PEEK_BYTES).string() }.getOrNull()
                }

                capture(
                    AnalyticsEvents.REQUEST_LINE_REQUEST_COMPLETED,
                    mapOf(
                        "authenticated" to false,
                        "status_code" to response.code,
                        "duration_ms" to elapsedMs(startedNanos),
                    ),
                )

                when (response.code) {
                    200 -> Result.Sent

                    // A banned device is shadow-banned: report success so the
                    // listener has nothing to route around. Matches iOS.
                    //
                    // Always recorded, because the listener-facing outcome is
                    // indistinguishable from success. ROM returns 403 from the
                    // ban check AND from its User-Agent gate, and Railway's
                    // edge can return one of its own, so a misconfiguration
                    // that swallows every Android request would otherwise be
                    // invisible on this side.
                    403 -> {
                        Log.i(TAG, "Request rejected (403) by request-o-matic: $errorBody")
                        capture(AnalyticsEvents.REQUEST_LINE_USER_BANNED, emptyMap())
                        Result.Sent
                    }

                    else -> {
                        Log.e(TAG, "Request failed code=${response.code} body=$errorBody")
                        Result.Failed(response.code)
                    }
                }
            }
        } catch (t: Throwable) {
            // Deliberately broad. This is the only guard around the send:
            // InfoViewModel launches into viewModelScope with no
            // CoroutineExceptionHandler, so anything escaping here reaches the
            // default uncaught handler and crashes the app instead of showing
            // "Network error" — which is what the pre-ROM code did for any
            // Throwable, not just IOException.
            Log.e(TAG, "Error sending request", t)
            capture(
                AnalyticsEvents.ERROR,
                mapOf(
                    "context" to REQUEST_LINE_ERROR_CONTEXT,
                    "description" to (t.message ?: t.toString()),
                    "error_type" to t.javaClass.simpleName,
                ),
            )
            Result.NetworkError(t)
        }
    }

    private fun elapsedMs(startedNanos: Long): Double =
        (System.nanoTime() - startedNanos) / 1_000_000.0

    companion object {
        const val FINGERPRINT_HEADER = "X-Device-Fingerprint"
        const val REQUEST_LINE_ERROR_CONTEXT = "request_line"

        /** Enough of an error body to diagnose one, bounded so a huge body can't be buffered. */
        private const val ERROR_BODY_PEEK_BYTES = 2048L
        private const val TAG = "RequestLineClient"
    }
}
