package org.wxyc.wxycapp.analytics

/**
 * The surface a play or pause is attributed to, written to the `source` property on
 * the `play` and `pause` events.
 *
 * **This enum is a subset of the iOS one and must stay that way.** Both apps report
 * into PostHog project 134292 with the same event names, so `source` is a single
 * shared breakdown: a value iOS does not define splits that breakdown in two, and a
 * value that means something different on each platform is worse still. The canonical
 * list lives in the iOS repo at
 * `Shared/Playback/Sources/PlaybackCore/PlaybackSource.swift`; `PlaybackAttributionTest`
 * asserts every value here appears there.
 *
 * iOS additionally defines `carPlay`, `widget`, `siri`, `watch` and `lockScreen`.
 * None are represented here, and none should be added speculatively: there is no Wear
 * app, no home-screen widget, Android Auto is not declared, and Assistant reaches the
 * player over the same MediaSession path as the notification, so nothing distinguishes
 * it. iOS's own `lockScreen` case documents the rule being followed — a case with no
 * distinguishing signal behind it over-claims precision, so it stays unmapped.
 *
 * Note that Android sessions are absent from any listening-hours figure: iOS
 * reconstructs those from `playback_heartbeat`, which has not been ported (see the
 * deferral in WXYC-Android#48).
 */
enum class PlaybackSource(val value: String) {
    /** The app's own play/pause control, driven through `PlayerViewModel`. */
    APP("app"),

    /**
     * The media notification, lock screen, a headset or Bluetooth transport button,
     * Android Auto, or Assistant. These are genuinely indistinguishable once they
     * reach the session, so they collapse into one bucket rather than being guessed
     * apart — the same compromise iOS makes for its own `remote`.
     */
    REMOTE("remote"),

    /**
     * System-driven, never a direct user action: an audio-focus interruption, a route
     * disconnect, or the service's own reconnect after the stream dropped.
     */
    AUTO("auto"),

    /**
     * No attributable surface. Reaching this means a playback state change arrived
     * with no controller command and no internal marker, which is a gap worth finding
     * rather than papering over by assuming the in-app control.
     */
    UNKNOWN("unknown")
}
