package com.example.basicmusicplayer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.example.basicmusicplayer.ui.screens.InfoScreenContent
import com.example.basicmusicplayer.ui.theme.WXYCTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class InfoScreen : ComponentActivity() {
    private val httpClient: OkHttpClient by lazy { OkHttpClient() }

    companion object {
        private const val TAG = "InfoScreen"
    }

    private suspend fun getWebhookUrl(): String {
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            WXYCTheme {
                InfoScreenContent(
                    onDialDJ = { dialDJ() },
                    onMakeRequest = { requestText -> sendRequest(requestText) },
                    onSendFeedback = { sendFeedback() }
                )
            }
        }
    }

    private fun dialDJ() {
        val dialIntent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:9199628989")
        }
        startActivity(dialIntent)
    }

    private fun sendFeedback() {
        val feedbackIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:feedback@wxyc.org?subject=Feedback%20on%20the%20WXYC%20Android%20app")
        }
        startActivity(feedbackIntent)
    }

    private fun sendRequest(requestText: String) {
        if (requestText.isBlank()) {
            Toast.makeText(this, "Please enter a request before sending.", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val webhookUrl = getWebhookUrl()

                if (webhookUrl.isEmpty()) {
                    Log.e(TAG, "Failed to fetch webhook URL from Railway endpoint")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@InfoScreen, "Failed to get webhook URL. Please try again.", Toast.LENGTH_SHORT).show()
                    }
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

                    withContext(Dispatchers.Main) {
                        if (isSuccessful) {
                            Toast.makeText(this@InfoScreen, "Request sent!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@InfoScreen, "Failed to send: ${response.code}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error sending request", t)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@InfoScreen, "Network error", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
