package org.wxyc.wxycapp.analytics

/**
 * Constants for PostHog analytics event names.
 * Mirrors the events tracked in the iOS app.
 */
object AnalyticsEvents {
    // App Lifecycle Events
    const val APP_LAUNCH = "app launch"
    const val APP_ENTERED_BACKGROUND = "App entered background"
    const val BACKGROUND_REFRESH_COMPLETED = "Background refresh completed"
    
    // Playback Events
    const val PLAYBACK_PLAY = "play"
    const val PLAYBACK_PAUSE = "pause"

    // Emitted when the live stream drops and when it comes back, so that
    // unattended playback stops are visible in analytics instead of only
    // showing up as an unexplained second "play" tap.
    const val STREAM_ERROR = "stream_error"
    const val STREAM_RECONNECTED = "stream_reconnected"
    
    // Navigation/UI Events
    const val PARTY_HORN_PRESENTED = "party horn presented"
    const val FEEDBACK_EMAIL_PRESENTED = "feedback email presented"
    const val FEEDBACK_EMAIL_SENT = "feedback email sent"
    const val PLAYCUT_DETAIL_OPENED = "playcut detail opened"
    const val PLAYCUT_DETAIL_VIEW_PRESENTED = "playcut detail view presented"
    const val STREAMING_LINK_TAPPED = "streaming link tapped"
    const val EXTERNAL_LINK_TAPPED = "external link tapped"
    
    // Integration Events
    const val WIDGET_TIMELINE_GENERATED = "widget timeline generated"
    const val SHARE_SHEET_PRESENTED = "share sheet presented"
    const val REQUEST_CREATED = "request created"
    
    // Error Event
    const val ERROR = "error"
}
