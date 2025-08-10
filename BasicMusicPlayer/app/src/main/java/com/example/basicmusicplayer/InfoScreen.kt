package com.example.basicmusicplayer

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import android.text.InputType
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class InfoScreen : AppCompatActivity() {
    private lateinit var btnDialADJ: Button
    private lateinit var btnMakeRequest: Button
    private lateinit var btnSendFeedback: Button
    private val requestIntent = Intent(Intent.ACTION_DIAL)
    private val feedbackIntent = Intent(Intent.ACTION_SENDTO)
    private val httpClient: OkHttpClient by lazy { OkHttpClient() }

    companion object {
        private const val TAG = "InfoScreen"
        // Note: Leading '@' and trailing whitespace removed from the provided URL
        private const val WEBHOOK_URL = "https://hooks.slack.com/services/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"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.info_screen)
        btnDialADJ = findViewById(R.id.btnDialADJ)
        btnMakeRequest = findViewById(R.id.btnMakeRequest)
        btnSendFeedback = findViewById(R.id.btnSendFeedback)

        btnDialADJ.setOnClickListener {
            requestIntent.data = Uri.parse("tel:9199628989")
            startActivity(requestIntent)
        }

        btnSendFeedback.setOnClickListener {
            feedbackIntent.data = Uri.parse("mailto:feedback@wxyc.org?subject=Feedback%20on%20the%20WXYC%20Android%20app")
            startActivity(feedbackIntent)
        }

        btnMakeRequest.setOnClickListener {
            showMakeRequestDialog()
        }
    }

    private fun showMakeRequestDialog() {
        val inputField = EditText(this).apply {
            hint = "Type your request..."
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setLines(3)
            maxLines = 5
        }

        AlertDialog.Builder(this)
            .setTitle("What would you like to request?")
            .setMessage("Please include song title and artist.")
            .setView(inputField)
            .setPositiveButton("Send") { dialog, _ ->
                val requestText = inputField.text?.toString()?.trim().orEmpty()
                if (requestText.isEmpty()) {
                    Toast.makeText(this, "Please enter a request before sending.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                Log.d(TAG, "Sending request: $requestText")
                sendRequest(requestText)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun sendRequest(requestText: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val payloadJson = JSONObject().put("text", requestText).toString()
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val requestBody = payloadJson.toRequestBody(mediaType)

                val httpRequest = Request.Builder()
                    .url(WEBHOOK_URL)
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
