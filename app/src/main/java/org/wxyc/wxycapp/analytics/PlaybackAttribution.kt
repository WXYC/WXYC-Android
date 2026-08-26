package org.wxyc.wxycapp.analytics

import androidx.media3.common.Player

/**
 * Where a play/pause command came from, as far as the media session can tell.
 *
 * Recorded in `MediaSession.Callback.onPlayerCommandRequest`, which receives the
 * issuing `ControllerInfo` directly, rather than inferred later from the player's
 * change reason — by the time `onPlayWhenReadyChanged` fires, the only thing left to
 * read is a reason code that says `USER_REQUEST` for the in-app button and the
 * notification alike.
 */
enum class CommandOrigin {
    /** A controller belonging to this app — the in-app play/pause control. */
    IN_APP,

    /** The media notification or lock-screen transport controls. */
    MEDIA_NOTIFICATION,

    /** Android Auto or Automotive. */
    ANDROID_AUTO,

    /** A connected controller this app doesn't recognise, e.g. a headset or Assistant. */
    OTHER_CONTROLLER,

    /**
     * Not a controller at all: the service's own reconnect path calling `play()`
     * straight on the player after the stream dropped.
     */
    AUTOMATIC_RECONNECT
}

/**
 * The `source` and `reason` a playback event should carry.
 *
 * `source` is a closed enum shared with iOS; `reason` is free text that may carry
 * Android-specific wording, and reuses the iOS phrasing wherever the same thing
 * happens on both platforms.
 */
data class PlaybackAttribution(val source: PlaybackSource, val reason: String?) {

    companion object {
        /**
         * Resolves a playback state change into the surface that caused it.
         *
         * @param playWhenReadyReason one of media3's `PLAY_WHEN_READY_CHANGE_REASON_*`
         *   values, which distinguishes a deliberate request from a system-driven one.
         * @param origin the controller that issued the command, when one did. A `null`
         *   origin on a `USER_REQUEST` is genuinely unattributable and resolves to
         *   [PlaybackSource.UNKNOWN] rather than being assumed to be the in-app control.
         */
        fun resolve(playWhenReadyReason: Int, origin: CommandOrigin?): PlaybackAttribution =
            when (playWhenReadyReason) {
                Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS ->
                    PlaybackAttribution(PlaybackSource.AUTO, "interruption began")

                Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY ->
                    PlaybackAttribution(PlaybackSource.AUTO, "route disconnected")

                Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM ->
                    PlaybackAttribution(PlaybackSource.AUTO, "stream ended")

                Player.PLAY_WHEN_READY_CHANGE_REASON_SUPPRESSED_TOO_LONG ->
                    PlaybackAttribution(PlaybackSource.AUTO, "suppressed too long")

                Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE ->
                    PlaybackAttribution(PlaybackSource.REMOTE, null)

                else -> fromOrigin(origin)
            }

        private fun fromOrigin(origin: CommandOrigin?): PlaybackAttribution = when (origin) {
            CommandOrigin.IN_APP -> PlaybackAttribution(PlaybackSource.APP, null)
            CommandOrigin.MEDIA_NOTIFICATION -> PlaybackAttribution(PlaybackSource.REMOTE, null)
            CommandOrigin.ANDROID_AUTO -> PlaybackAttribution(PlaybackSource.REMOTE, null)
            CommandOrigin.OTHER_CONTROLLER -> PlaybackAttribution(PlaybackSource.REMOTE, null)
            CommandOrigin.AUTOMATIC_RECONNECT ->
                PlaybackAttribution(PlaybackSource.AUTO, "resume after stream reconnect")
            null -> PlaybackAttribution(PlaybackSource.UNKNOWN, null)
        }
    }
}
