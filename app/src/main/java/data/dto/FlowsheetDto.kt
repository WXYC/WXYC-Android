package data.dto

import org.wxyc.wxycapp.data.Playcut
import org.wxyc.wxycapp.data.PlaycutMetadata
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class FlowsheetResponseDto(
    val entries: List<FlowsheetEntryDto>
)

data class FlowsheetEntryDto(
    val id: Int,
    val showId: Int,
    val playOrder: Int,
    val addTime: String,
    val entryType: String,
    val artistName: String? = null,
    val albumTitle: String? = null,
    val trackTitle: String? = null,
    val recordLabel: String? = null,
    val rotationBin: String? = null,
    val artworkUrl: String? = null,
    val discogsUrl: String? = null,
    val releaseYear: Int? = null,
    val spotifyUrl: String? = null,
    val appleMusicUrl: String? = null,
    val youtubeMusicUrl: String? = null,
    val bandcampUrl: String? = null,
    val soundcloudUrl: String? = null,
    val artistBio: String? = null,
    val artistWikipediaUrl: String? = null
) {
    fun toDomain(): Playcut {
        val metadata = if (entryType == "track") {
            PlaycutMetadata(
                label = recordLabel,
                releaseYear = releaseYear,
                discogsURL = discogsUrl,
                artistBio = artistBio,
                wikipediaURL = artistWikipediaUrl,
                spotifyURL = spotifyUrl,
                appleMusicURL = appleMusicUrl,
                youtubeMusicURL = youtubeMusicUrl,
                bandcampURL = bandcampUrl,
                soundcloudURL = soundcloudUrl
            )
        } else null

        return Playcut(
            id = id,
            entryType = entryType,
            hour = parseAddTime(addTime),
            showId = showId,
            playOrder = playOrder,
            rotation = rotationBin,
            songTitle = trackTitle,
            labelName = recordLabel,
            artistName = artistName,
            releaseTitle = albumTitle,
            imageURL = artworkUrl,
            metadata = metadata
        )
    }
}

// api.wxyc.org sends UTC timestamps like "2026-09-11T17:06:37.085Z"; parsed manually
// rather than via java.time since that needs API 26+ against this app's minSdk 24.
private fun parseAddTime(addTime: String): Long {
    return try {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        formatter.parse(addTime)?.time ?: System.currentTimeMillis()
    } catch (e: Exception) {
        System.currentTimeMillis()
    }
}
