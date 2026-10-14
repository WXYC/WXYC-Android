package org.wxyc.wxycapp.auth

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.core.content.edit
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import java.io.IOException
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts and decrypts the stored session. */
interface SessionCipher {
    /** Encrypts [plaintext]. */
    fun encrypt(plaintext: ByteArray): ByteArray

    /** Decrypts what [encrypt] produced; throws when it cannot. */
    fun decrypt(ciphertext: ByteArray): ByteArray
}

/**
 * A [SessionStore] that keeps the session encrypted by [cipher] in [prefs].
 *
 * A value that will not decrypt or parse is cleared and reported as no session. That is
 * the expected state after a backup restore to a new device: the app allows backup, but
 * Keystore keys are not backed up, so the restored ciphertext cannot be read there.
 */
class EncryptedSessionStore(
    private val prefs: SharedPreferences,
    private val cipher: SessionCipher,
) : SessionStore {

    override fun load(): AuthSession? {
        val stored = prefs.getString(KEY, null) ?: return null
        return try {
            val bytes = stored.decodeBase64()?.toByteArray() ?: throw IOException("not base64")
            val json = JsonParser.parseString(cipher.decrypt(bytes).decodeToString()).asJsonObject
            AuthSession(
                sessionToken = json["sessionToken"].asString,
                jwt = json["jwt"].asString,
                userId = json["userId"].asString,
                jwtExpiresAtEpochSeconds = json["jwtExpiresAtEpochSeconds"].asLong,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Discarding an unreadable auth session: ${e.javaClass.simpleName}")
            clear()
            null
        }
    }

    // The KTX edit helper drops commit()'s result, and that result is how a failed write is seen.
    @SuppressLint("UseKtx")
    override fun save(session: AuthSession) {
        val json = JsonObject().apply {
            addProperty("sessionToken", session.sessionToken)
            addProperty("jwt", session.jwt)
            addProperty("userId", session.userId)
            addProperty("jwtExpiresAtEpochSeconds", session.jwtExpiresAtEpochSeconds)
        }
        val encoded = cipher.encrypt(json.toString().encodeToByteArray()).toByteString().base64()
        if (!prefs.edit().putString(KEY, encoded).commit()) throw IOException("Session write did not commit")
    }

    override fun clear() {
        prefs.edit(commit = true) { remove(KEY) }
    }

    companion object {
        /** The private preferences file this store writes to. */
        const val PREFS_NAME = "auth_session"
        private const val KEY = "session"
        private const val TAG = "EncryptedSessionStore"
    }
}

/** AES-GCM with a key that never leaves the Android Keystore. Output is the IV followed by the ciphertext. */
class KeystoreSessionCipher(private val alias: String = "wxyc_auth_session") : SessionCipher {

    override fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        try {
            cipher.init(Cipher.ENCRYPT_MODE, key())
        } catch (e: KeyPermanentlyInvalidatedException) {
            discardKey()
            cipher.init(Cipher.ENCRYPT_MODE, key())
        }
        return cipher.iv + cipher.doFinal(plaintext)
    }

    override fun decrypt(ciphertext: ByteArray): ByteArray {
        val spec = GCMParameterSpec(TAG_BITS, ciphertext, 0, IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        try {
            cipher.init(Cipher.DECRYPT_MODE, key(), spec)
        } catch (e: KeyPermanentlyInvalidatedException) {
            // Whatever this key encrypted is gone; the store clears it and signs in again.
            discardKey()
            throw e
        }
        return cipher.doFinal(ciphertext, IV_BYTES, ciphertext.size - IV_BYTES)
    }

    /**
     * The alias's AES key, generating it if absent. Only an unrecoverable entry is replaced:
     * one whose key throws [UnrecoverableKeyException], loads as null, or is not a secret
     * key. Any other Keystore error propagates and the key is left alone.
     */
    @Synchronized
    private fun key(): SecretKey {
        val keyStore = keyStore()
        val existing = try {
            keyStore.getKey(alias, null)
        } catch (e: UnrecoverableKeyException) {
            null
        }
        if (existing is SecretKey) return existing
        if (keyStore.containsAlias(alias)) {
            Log.w(TAG, "Regenerating an auth session key that would not load")
            keyStore.deleteEntry(alias)
        }
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply { init(spec) }.generateKey()
    }

    @Synchronized
    private fun discardKey() {
        Log.w(TAG, "Discarding a permanently invalidated auth session key")
        keyStore().deleteEntry(alias)
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private companion object {
        const val TAG = "KeystoreSessionCipher"
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
