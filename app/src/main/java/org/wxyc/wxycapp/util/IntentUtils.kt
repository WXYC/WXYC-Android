package org.wxyc.wxycapp.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast

fun launchIntentSafely(context: Context, intent: Intent, fallbackMessage: String) {
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, fallbackMessage, Toast.LENGTH_SHORT).show()
    }
}
