package org.wxyc.wxycapp.data

/**
 * Extended metadata for a Playcut, fetched from external services
 */
data class PlaycutMetadata(
    // MARK: - Discogs Metadata
    
    /** Record label name */
    val label: String? = null,
    
    /** Release year */
    val releaseYear: Int? = null,
    
    /** Link to the release on Discogs */
    val discogsURL: String? = null,
    
    /** Artist biography from Discogs */
    val artistBio: String? = null,
    
    /** Link to artist's Wikipedia page */
    val wikipediaURL: String? = null,
    
    // MARK: - Streaming Platform Links
    
    /** Link to track/album on Spotify */
    val spotifyURL: String? = null,
    
    /** Link to track/album on Apple Music */
    val appleMusicURL: String? = null,
    
    /** Link to track/album on YouTube Music */
    val youtubeMusicURL: String? = null,
    
    /** Link to track/album on Bandcamp */
    val bandcampURL: String? = null,
    
    /** Link to track/album on SoundCloud */
    val soundcloudURL: String? = null
) {
    /** Check if any streaming links are available */
    val hasStreamingLinks: Boolean
        get() = spotifyURL != null ||
                appleMusicURL != null ||
                youtubeMusicURL != null ||
                bandcampURL != null ||
                soundcloudURL != null
    
    companion object {
        /** Empty metadata instance */
        val EMPTY = PlaycutMetadata()
    }
}
