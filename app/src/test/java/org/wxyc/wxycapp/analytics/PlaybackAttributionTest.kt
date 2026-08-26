package org.wxyc.wxycapp.analytics

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackAttributionTest {

    @Test
    fun `the in-app control attributes to app`() {
        val attribution = PlaybackAttribution.resolve(
            Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
            CommandOrigin.IN_APP
        )

        assertEquals(PlaybackSource.APP, attribution.source)
        assertNull(attribution.reason)
    }

    @Test
    fun `the media notification attributes to remote`() {
        val attribution = PlaybackAttribution.resolve(
            Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
            CommandOrigin.MEDIA_NOTIFICATION
        )

        assertEquals(PlaybackSource.REMOTE, attribution.source)
    }

    @Test
    fun `Android Auto collapses into remote rather than claiming its own surface`() {
        // iOS collapses Lock Screen, Control Center and CarPlay's built-in transport
        // into `remote` because MPRemoteCommandCenter cannot tell them apart. The same
        // principle applies here: a case with no distinguishing signal behind it
        // over-claims precision. See PlaybackSource.swift's `lockScreen` doc comment.
        val attribution = PlaybackAttribution.resolve(
            Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
            CommandOrigin.ANDROID_AUTO
        )

        assertEquals(PlaybackSource.REMOTE, attribution.source)
    }

    @Test
    fun `an unrecognised controller attributes to remote, not app`() {
        val attribution = PlaybackAttribution.resolve(
            Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
            CommandOrigin.OTHER_CONTROLLER
        )

        assertEquals(PlaybackSource.REMOTE, attribution.source)
    }

    @Test
    fun `losing audio focus is automatic and reuses the iOS interruption wording`() {
        val attribution = PlaybackAttribution.resolve(
            Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS,
            null
        )

        assertEquals(PlaybackSource.AUTO, attribution.source)
        assertEquals("interruption began", attribution.reason)
    }

    @Test
    fun `unplugging headphones is automatic and reuses the iOS route wording`() {
        val attribution = PlaybackAttribution.resolve(
            Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY,
            null
        )

        assertEquals(PlaybackSource.AUTO, attribution.source)
        assertEquals("route disconnected", attribution.reason)
    }

    @Test
    fun `the stream ending is automatic`() {
        val attribution = PlaybackAttribution.resolve(
            Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM,
            null
        )

        assertEquals(PlaybackSource.AUTO, attribution.source)
        assertEquals("stream ended", attribution.reason)
    }

    @Test
    fun `suppression timing out is automatic`() {
        val attribution = PlaybackAttribution.resolve(
            Player.PLAY_WHEN_READY_CHANGE_REASON_SUPPRESSED_TOO_LONG,
            null
        )

        assertEquals(PlaybackSource.AUTO, attribution.source)
        assertEquals("suppressed too long", attribution.reason)
    }

    @Test
    fun `a reconnect resumes automatically rather than looking like a user tap`() {
        // reconnectNow() calls play() straight on the player, so no controller command
        // is issued and the origin would otherwise be unattributable. Without this,
        // every recovered stream drop would inflate the user-initiated play count.
        val attribution = PlaybackAttribution.resolve(
            Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
            CommandOrigin.AUTOMATIC_RECONNECT
        )

        assertEquals(PlaybackSource.AUTO, attribution.source)
        assertEquals("resume after stream reconnect", attribution.reason)
    }

    @Test
    fun `an unattributable request is unknown rather than assumed to be the app`() {
        val attribution = PlaybackAttribution.resolve(
            Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
            null
        )

        assertEquals(PlaybackSource.UNKNOWN, attribution.source)
        assertNull(attribution.reason)
    }

    @Test
    fun `a remote-origin change attributes to remote whatever the recorded origin`() {
        val attribution = PlaybackAttribution.resolve(
            Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE,
            null
        )

        assertEquals(PlaybackSource.REMOTE, attribution.source)
    }

    @Test
    fun `every source value is one the iOS enum already defines`() {
        // The cross-platform contract: both apps write `source` on the same events in
        // the same PostHog project, so this enum must stay a subset of iOS's. Adding a
        // value iOS does not have splits a shared breakdown in two. Kept in sync with
        // Shared/Playback/Sources/PlaybackCore/PlaybackSource.swift.
        val iosVocabulary = setOf(
            "app", "carPlay", "widget", "siri", "watch", "lockScreen", "remote", "auto", "unknown"
        )

        PlaybackSource.values().forEach { source ->
            assertTrue(
                "`${source.value}` is not a value the iOS PlaybackSource enum defines",
                source.value in iosVocabulary
            )
        }
    }
}
