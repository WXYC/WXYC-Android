package org.wxyc.wxycapp.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.wxyc.wxycapp.BuildConfig
import javax.inject.Inject

@HiltViewModel
class InfoViewModel @Inject constructor() : ViewModel() {

    private val httpClient: OkHttpClient by lazy { OkHttpClient() }

    private val _requestStatus = MutableSharedFlow<String>()
    val requestStatus: SharedFlow<String> = _requestStatus.asSharedFlow()

    companion object {
        private const val TAG = "InfoViewModel"
    }

    fun makeRequest(requestText: String) {
        if (requestText.isBlank()) {
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val webhookUrl = getWebhookUrl()

                if (webhookUrl.isEmpty()) {
                    Log.e(TAG, "Failed to fetch webhook URL from Railway endpoint")
                    _requestStatus.emit("Failed to get webhook URL. Please try again.")
                    return@launch
                }

                Log.i(TAG, "Sending request to webhook URL $webhookUrl")

                val payloadJson = JSONObject().put("text", requestText).toString()
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val requestBody = payloadJson.toRequestBody(mediaType)

                val httpRequest = Request.Builder()
                    .url(webhookUrl)
                    .addHeader("Content-Type", "application/json")
                    .method("POST", requestBody)
                    .build()

                httpClient.newCall(httpRequest).execute().use { response ->
                    val isSuccessful = response.isSuccessful
                    val responseBody = response.body?.string()
                    Log.d(TAG, "Slack webhook response code=${response.code} body=$responseBody")

                    if (isSuccessful) {
                        _requestStatus.emit("Request sent!")
                    } else {
                        _requestStatus.emit("Failed to send: ${response.code}")
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error sending request", t)
                _requestStatus.emit("Network error")
            }
        }
    }

    private fun getWebhookUrl(): String {
        return try {
            val request = Request.Builder()
                .url(BuildConfig.RAILWAY_ENDPOINT_URL)
                .build()

            Log.i(TAG, "Fetching webhook URL from Railway endpoint ${BuildConfig.RAILWAY_ENDPOINT_URL}")

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val url = BuildConfig.SLACK_WEBHOOK_BASE_URL + response.body?.string()?.trim()
                    Log.d(TAG, "Fetched webhook URL from Railway endpoint")
                    url ?: ""
                } else {
                    Log.e(TAG, "Failed to fetch webhook URL: ${response.code}")
                    ""
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching webhook URL from Railway endpoint", e)
            ""
        }
    }
}
