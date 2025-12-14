package org.wxyc.wxycapp.data

import androidx.compose.ui.graphics.Color

/**
 * Streaming services for music playback
 */
sealed class StreamingService(
    val displayName: String,
    val iconResourceName: String,
    val brandColor: Color,
    val useCustomIcon: Boolean,
    val opensInBrowser: Boolean = false
) {
    data object Spotify : StreamingService(
        displayName = "Spotify",
        iconResourceName = "ic_spotify",
        brandColor = Color(0xFF1DB954), // Spotify green
        useCustomIcon = true
    )
    
    data object AppleMusic : StreamingService(
        displayName = "Apple Music",
        iconResourceName = "ic_apple_music",
        brandColor = Color(0xFFFA233B), // Apple Music red
        useCustomIcon = true
    )
    
    data object YouTubeMusic : StreamingService(
        displayName = "YouTube Music",
        iconResourceName = "play_circle_filled", // Material icon
        brandColor = Color(0xFFFF0000), // YouTube red
        useCustomIcon = false
    )
    
    data object Bandcamp : StreamingService(
        displayName = "Bandcamp",
        iconResourceName = "ic_bandcamp",
        brandColor = Color(0xFF629AA9), // Bandcamp blue
        useCustomIcon = true
    )
    
    data object SoundCloud : StreamingService(
        displayName = "SoundCloud",
        iconResourceName = "waveform", // Material icon
        brandColor = Color(0xFFFF5500), // SoundCloud orange
        useCustomIcon = false,
        opensInBrowser = true // SoundCloud links open in browser to preserve query params
    )
    
    companion object {
        fun all() = listOf(Spotify, AppleMusic, YouTubeMusic, Bandcamp, SoundCloud)
    }
}
