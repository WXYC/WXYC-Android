package org.wxyc.wxycapp.requestline

import android.content.SharedPreferences
import androidx.core.content.edit
import java.util.UUID

/**
 * A random per-install UUID sent as `X-Device-Fingerprint`, so an abusive
 * listener can be banned from the request line. It identifies the install,
 * not the person: clearing app data mints a new one.
 */
class DeviceFingerprintStore(private val prefs: SharedPreferences) {

    @Synchronized
    fun get(): String {
        prefs.getString(KEY, null)
            ?.takeIf { isUuid(it) }
            ?.let { return it }

        return UUID.randomUUID().toString().also {
            prefs.edit { putString(KEY, it) }
        }
    }

    private fun isUuid(value: String): Boolean =
        runCatching { UUID.fromString(value).toString() == value.lowercase() }.getOrDefault(false)

    companion object {
        const val PREFS_NAME = "request_line"
        private const val KEY = "device_fingerprint"
    }
}
