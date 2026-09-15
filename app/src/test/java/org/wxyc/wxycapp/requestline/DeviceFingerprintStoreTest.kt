package org.wxyc.wxycapp.requestline

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.RETURNS_SELF
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.UUID

class DeviceFingerprintStoreTest {

    private val editor = mock(SharedPreferences.Editor::class.java, RETURNS_SELF)
    private val prefs = mock(SharedPreferences::class.java).also {
        `when`(it.edit()).thenReturn(editor)
    }

    @Test
    fun `returns the stored fingerprint`() {
        val stored = "11111111-2222-3333-4444-555555555555"
        `when`(prefs.getString(anyString(), eq(null))).thenReturn(stored)

        assertEquals(stored, DeviceFingerprintStore(prefs).get())
        verify(prefs, never()).edit()
    }

    @Test
    fun `mints and persists a UUID when none is stored`() {
        `when`(prefs.getString(anyString(), eq(null))).thenReturn(null)

        val minted = DeviceFingerprintStore(prefs).get()

        UUID.fromString(minted)
        verify(editor).putString(anyString(), eq(minted))
        verify(editor).apply()
    }

    @Test
    fun `replaces a malformed stored value`() {
        `when`(prefs.getString(anyString(), eq(null))).thenReturn("not-a-uuid")

        val minted = DeviceFingerprintStore(prefs).get()

        assertNotEquals("not-a-uuid", minted)
        UUID.fromString(minted)
    }
}
