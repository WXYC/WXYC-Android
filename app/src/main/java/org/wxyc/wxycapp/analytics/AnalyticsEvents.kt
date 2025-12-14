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
    
    // Navigation/UI Events
    const val PARTY_HORN_PRESENTED = "party horn presented"
    const val FEEDBACK_EMAIL_PRESENTED = "feedback email presented"
    const val FEEDBACK_EMAIL_SENT = "feedback email sent"
    const val PLAYCUT_DETAIL_OPENED = "playcut detail opened"
    
    // Integration Events
    const val WIDGET_TIMELINE_GENERATED = "widget timeline generated"
    const val SHARE_SHEET_PRESENTED = "share sheet presented"
    const val REQUEST_CREATED = "request created"
    
    // Error Event
    const val ERROR = "error"
}
