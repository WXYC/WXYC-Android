package org.wxyc.wxycapp.analytics

/**
 * PostHog event names.
 *
 * These are not merely "mirrors" of the iOS names — they are the *same* names in the
 * *same* project (134292), written with the same API key. A name that differs from
 * iOS's for the same concept forks a shared series in two; a name iOS has retired
 * lands in the old leg of the union actions that bridge the 3.2 rename, so that bridge
 * can never decay to zero. `AnalyticsEventNamesTest` holds both lines.
 *
 * Only events the app actually emits belong here. Constants for events Android has no
 * surface for were removed in WXYC-Android#48: an unused constant carrying a stale name
 * is what gets wired up later with the wrong spelling.
 *
 * Platform is carried by `$lib` (`posthog-android` against `posthog-ios`), not by the
 * event name. See the ADR at `docs/adr/0001-shared-posthog-project.md`.
 */
object AnalyticsEvents {
    // App lifecycle
    const val APP_LAUNCH = "app_launch"

    // Playback
    const val PLAYBACK_PLAY = "play"
    const val PLAYBACK_PAUSE = "pause"

    // Emitted when the live stream drops and when it comes back, so that unattended
    // playback stops are visible in analytics instead of only showing up as an
    // unexplained second "play" tap.
    const val STREAM_ERROR = "stream_error"
    const val STREAM_RECONNECTED = "stream_reconnected"

    // Navigation / UI
    const val PLAYCUT_DETAIL_VIEW_PRESENTED = "playcut_detail_view_presented"
    const val STREAMING_LINK_TAPPED = "streaming_link_tapped"
    const val EXTERNAL_LINK_TAPPED = "external_link_tapped"

    // Error
    const val ERROR = "error"
}
