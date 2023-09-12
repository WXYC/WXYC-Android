package com.example.basicmusicplayer

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.content.Intent
import android.net.Uri

class InfoScreen : AppCompatActivity() {
    private lateinit var btnRequest: Button
    private lateinit var btnSendFeedback: Button
    private val requestIntent = Intent(Intent.ACTION_DIAL)
    private val feedbackIntent = Intent(Intent.ACTION_SENDTO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.info_screen)
        btnRequest = findViewById(R.id.btnRequest)
        btnSendFeedback = findViewById(R.id.btnSendFeedback)

        btnRequest.setOnClickListener {
            requestIntent.data = Uri.parse("tel:9199628989")
            startActivity(requestIntent)
        }

        btnSendFeedback.setOnClickListener {
            feedbackIntent.data = Uri.parse("mailto:feedback@wxyc.org?subject=Feedback%20on%20the%20WXYC%20Android%20app")
            startActivity(feedbackIntent)
        }
    }

}
