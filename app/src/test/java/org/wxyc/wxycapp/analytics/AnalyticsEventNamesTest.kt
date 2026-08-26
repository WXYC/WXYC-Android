package org.wxyc.wxycapp.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mirrors the iOS `EventNameStabilityTests` suite. Both apps report into one PostHog
 * project, so an accidental rename here doesn't just break an Android chart — it
 * silently forks a shared series in two.
 */
class AnalyticsEventNamesTest {

    @Test
    fun `event names match the iOS spelling exactly`() {
        assertEquals("app_launch", AnalyticsEvents.APP_LAUNCH)
        assertEquals("play", AnalyticsEvents.PLAYBACK_PLAY)
        assertEquals("pause", AnalyticsEvents.PLAYBACK_PAUSE)
        assertEquals("stream_error", AnalyticsEvents.STREAM_ERROR)
        assertEquals("stream_reconnected", AnalyticsEvents.STREAM_RECONNECTED)
        assertEquals("playcut_detail_view_presented", AnalyticsEvents.PLAYCUT_DETAIL_VIEW_PRESENTED)
        assertEquals("streaming_link_tapped", AnalyticsEvents.STREAMING_LINK_TAPPED)
        assertEquals("external_link_tapped", AnalyticsEvents.EXTERNAL_LINK_TAPPED)
        assertEquals("error", AnalyticsEvents.ERROR)
    }

    @Test
    fun `no event name is a space-separated one iOS retired`() {
        // iOS snake_cased every event name in 3.2. Emitting a space-separated name
        // revives a retired one and lands in the old leg of the union actions that
        // bridge the rename, so that bridge can never decay to zero and retire.
        eventNames().forEach { name ->
            assertFalse("`$name` contains a space; iOS retired those names in 3.2", name.contains(' '))
        }
    }

    @Test
    fun `every declared constant is one the app actually emits`() {
        // Nine constants used to sit here unreferenced, mirroring iOS events Android
        // has no surface for. An unused constant carrying a stale name is exactly what
        // gets wired up later with the wrong spelling.
        val expected = setOf(
            "app_launch",
            "play",
            "pause",
            "stream_error",
            "stream_reconnected",
            "playcut_detail_view_presented",
            "streaming_link_tapped",
            "external_link_tapped",
            "error"
        )

        assertEquals(expected, eventNames().toSet())
    }

    @Test
    fun `event names are lowercase`() {
        eventNames().forEach { name ->
            assertTrue("`$name` is not lowercase", name == name.lowercase())
        }
    }

    private fun eventNames(): List<String> =
        AnalyticsEvents::class.java.declaredFields
            .filter { it.type == String::class.java }
            .mapNotNull { it.isAccessible = true; it.get(AnalyticsEvents) as? String }
}
