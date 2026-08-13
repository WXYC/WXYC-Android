package org.wxyc.wxycapp.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.MockedStatic

class IntentUtilsTest {

    private lateinit var mockContext: Context
    private lateinit var mockIntent: Intent
    private lateinit var toastMock: MockedStatic<Toast>
    private lateinit var mockToast: Toast

    @Before
    fun setUp() {
        mockContext = mock(Context::class.java)
        mockIntent = mock(Intent::class.java)
        mockToast = mock(Toast::class.java)
        toastMock = mockStatic(Toast::class.java)
        toastMock.`when`<Toast> {
            Toast.makeText(mockContext, "fallback message", Toast.LENGTH_SHORT)
        }.thenReturn(mockToast)
    }

    @After
    fun tearDown() {
        toastMock.close()
    }

    @Test
    fun `launches the intent when a handler exists`() {
        launchIntentSafely(mockContext, mockIntent, "fallback message")

        verify(mockContext).startActivity(mockIntent)
        toastMock.verifyNoInteractions()
    }

    @Test
    fun `shows the fallback message instead of crashing when no handler exists`() {
        doThrow(ActivityNotFoundException::class.java).`when`(mockContext).startActivity(mockIntent)

        launchIntentSafely(mockContext, mockIntent, "fallback message")

        verify(mockToast).show()
    }
}
