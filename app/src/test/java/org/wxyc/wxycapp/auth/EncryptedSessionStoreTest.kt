package org.wxyc.wxycapp.auth

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.RETURNS_SELF
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.IOException
import java.security.GeneralSecurityException

/** Reversible, obviously-not-secure cipher so tests can see what reaches storage. */
private class XorCipher(private val key: Byte = 0x5A) : SessionCipher {
    var failDecrypt = false
    override fun encrypt(plaintext: ByteArray) = ByteArray(plaintext.size) { (plaintext[it].toInt() xor key.toInt()).toByte() }
    override fun decrypt(ciphertext: ByteArray): ByteArray {
        if (failDecrypt) throw GeneralSecurityException("key is gone")
        return encrypt(ciphertext)
    }
}

class EncryptedSessionStoreTest {

    private val values = mutableMapOf<String, String>()
    private var commitSucceeds = true
    private val editor = mock(SharedPreferences.Editor::class.java, RETURNS_SELF).also { editor ->
        doAnswer { values[it.getArgument(0)] = it.getArgument(1); editor }.`when`(editor).putString(anyString(), anyString())
        doAnswer { values.remove(it.getArgument<String>(0)); editor }.`when`(editor).remove(anyString())
        doAnswer { commitSucceeds }.`when`(editor).commit()
    }
    private val prefs = mock(SharedPreferences::class.java).also { prefs ->
        `when`(prefs.edit()).thenReturn(editor)
        doAnswer { values[it.getArgument(0)] }.`when`(prefs).getString(anyString(), org.mockito.ArgumentMatchers.isNull())
    }
    private val cipher = XorCipher()
    private val store = EncryptedSessionStore(prefs, cipher)

    private val session = AuthSession(
        sessionToken = "session-token",
        jwt = "header.payload.sig",
        userId = "user-1",
        jwtExpiresAtEpochSeconds = 1_800_000_000L,
    )

    @Test
    fun `round-trips a session`() {
        store.save(session)

        assertEquals(session, store.load())
    }

    @Test
    fun `never writes a token in the clear`() {
        store.save(session)

        val stored = values.values.joinToString()
        assertFalse(stored.contains("session-token"))
        assertFalse(stored.contains("header.payload.sig"))
    }

    @Test
    fun `loads nothing when nothing is stored`() {
        assertNull(store.load())
    }

    @Test
    fun `treats a blob that will not decrypt as absent and clears it`() {
        store.save(session)
        cipher.failDecrypt = true

        assertNull(store.load())
        assertEquals(emptyMap<String, String>(), values)
    }

    @Test
    fun `treats unreadable contents as absent and clears them`() {
        val garbage = listOf("%%%", "", cipherText("not json"), cipherText("""{"userId":"u"}"""))
        for (value in garbage) {
            values[KEY] = value
            assertNull("for '$value'", store.load())
            assertEquals(emptyMap<String, String>(), values)
        }
    }

    @Test
    fun `reports a save that did not commit`() {
        commitSucceeds = false

        assertThrows(IOException::class.java) { store.save(session) }
    }

    @Test
    fun `clear removes the session`() {
        store.save(session)

        store.clear()

        assertNull(store.load())
    }

    private fun cipherText(json: String): String {
        val bytes = cipher.encrypt(json.toByteArray())
        return okio.ByteString.of(*bytes).base64()
    }

    private companion object {
        const val KEY = "session"
    }
}
