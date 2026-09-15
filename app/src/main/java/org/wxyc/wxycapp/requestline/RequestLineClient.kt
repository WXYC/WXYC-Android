package org.wxyc.wxycapp.requestline

import android.util.Log
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

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
) {

    sealed interface Result {
        object Sent : Result
        data class Failed(val statusCode: Int) : Result
        data class NetworkError(val cause: IOException) : Result
    }

    fun send(message: String): Result {
        val body = JsonObject().apply { addProperty("message", message) }.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(endpoint)
            .header("User-Agent", userAgent)
            .apply { fingerprint()?.let { header(FINGERPRINT_HEADER, it) } }
            .post(body)
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                when (response.code) {
                    200 -> Result.Sent
                    // A banned device is shadow-banned: report success so the
                    // listener has nothing to route around. Matches iOS.
                    403 -> {
                        Log.i(TAG, "Request shadow-banned by request-o-matic")
                        Result.Sent
                    }
                    else -> {
                        Log.e(TAG, "Request failed code=${response.code} body=${response.body?.string()}")
                        Result.Failed(response.code)
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error sending request", e)
            Result.NetworkError(e)
        }
    }

    companion object {
        const val REQUEST_O_MATIC_URL = "https://request-o-matic-production.up.railway.app/request"
        const val FINGERPRINT_HEADER = "X-Device-Fingerprint"
        private const val TAG = "RequestLineClient"
    }
}
