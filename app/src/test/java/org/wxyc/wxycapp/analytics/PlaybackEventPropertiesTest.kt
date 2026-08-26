package org.wxyc.wxycapp.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PlaybackEventPropertiesTest {

    private val appTap = PlaybackAttribution(PlaybackSource.APP, null)
    private val interrupted = PlaybackAttribution(PlaybackSource.AUTO, "interruption began")

    @Test
    fun `source is written as the shared string value, not the Kotlin enum name`() {
        // `APP.toString()` would send "APP", which matches nothing iOS writes.
        val properties = PlaybackEventProperties.build(appTap, durationSeconds = null, sessionId = null)

        assertEquals("app", properties["source"])
    }

    @Test
    fun `a null reason is omitted rather than sent as the string null`() {
        val properties = PlaybackEventProperties.build(appTap, durationSeconds = null, sessionId = null)

        assertFalse(properties.containsKey("reason"))
    }

    @Test
    fun `a reason is included when there is one`() {
        val properties = PlaybackEventProperties.build(interrupted, durationSeconds = null, sessionId = null)

        assertEquals("interruption began", properties["reason"])
    }

    @Test
    fun `an unknown duration is omitted rather than substituted with zero`() {
        // Anything substituted here is averaged in as though it were a real listen.
        // Omitting leaves the property null, which reads as a pre-instrumentation row.
        val properties = PlaybackEventProperties.build(appTap, durationSeconds = null, sessionId = null)

        assertFalse(properties.containsKey("duration"))
    }

    @Test
    fun `a known duration is included in seconds`() {
        val properties = PlaybackEventProperties.build(appTap, durationSeconds = 90.5, sessionId = null)

        assertEquals(90.5, properties["duration"] as Double, 0.0001)
    }

    @Test
    fun `session_id is included when a listen is in progress`() {
        val properties = PlaybackEventProperties.build(appTap, durationSeconds = null, sessionId = "abc-123")

        assertEquals("abc-123", properties["session_id"])
    }

    @Test
    fun `session_id is omitted when there is no active listen`() {
        val properties = PlaybackEventProperties.build(appTap, durationSeconds = null, sessionId = null)

        assertFalse(properties.containsKey("session_id"))
    }

    @Test
    fun `no property is ever null-valued`() {
        // PostHog renders a null-valued property as an explicit null in breakdowns,
        // which is not the same as the property being absent.
        val properties = PlaybackEventProperties.build(interrupted, durationSeconds = 12.0, sessionId = "s")

        properties.values.forEach { assertFalse(it == "null") }
        assertEquals(setOf("source", "reason", "duration", "session_id"), properties.keys)
    }
}
