package data.dto

import android.util.Log
import org.wxyc.wxycapp.data.Playcut
import org.wxyc.wxycapp.data.PlaycutMetadata
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class FlowsheetResponseDto(
    val entries: List<FlowsheetEntryDto>
)

// Covers all eight FlowsheetV2*Entry variants api.yaml's discriminator lists
// (track, show_start, show_end, dj_join, dj_leave, talkset, breakpoint, message)
// as one flat, all-nullable-but-track-fields shape rather than a sealed hierarchy,
// since Gson has no sum-type support without a registered discriminator adapter.
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
    val artistWikipediaUrl: String? = null,
    val message: String? = null,
    val djName: String? = null,
    val radioHour: String? = null
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
            hour = parseTimestamp(addTime),
            showId = showId,
            playOrder = playOrder,
            rotation = rotationBin,
            songTitle = trackTitle,
            labelName = recordLabel,
            artistName = artistName,
            releaseTitle = albumTitle,
            imageURL = artworkUrl,
            metadata = metadata,
            displayMessage = markerLabel()
        )
    }

    private fun markerLabel(): String? = when (entryType) {
        "talkset", "message" -> message
        // Prefer the server's own label (always correct) over deriving one from a
        // timestamp: radio_hour is the exact top-of-hour but is null pending a
        // backend backfill, and add_time is "~1 min before the hour" per api.yaml,
        // so formatting add_time directly reads the wrong hour about half the time.
        "breakpoint" -> message ?: parseTimestamp(radioHour ?: addTime)?.let { formatHourLabel(it) }
        "show_start" -> djName?.let { "$it signed on" }
        "show_end" -> djName?.let { "$it signed off" }
        "dj_join" -> djName?.let { "$it joined" }
        "dj_leave" -> djName?.let { "$it left" }
        else -> null
    }
}

// api.wxyc.org sends UTC timestamps like "2026-09-11T17:06:37.085Z"; parsed manually
// rather than via java.time since that needs API 26+ against this app's minSdk 24.
// Returns null rather than defaulting to "now" on a parse failure — a silent "now"
// fallback would be indistinguishable from a freshly logged row.
private fun parseTimestamp(value: String?): Long? {
    if (value == null) return null
    return try {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        formatter.parse(value)?.time
    } catch (e: Exception) {
        Log.w("FlowsheetDto", "Failed to parse timestamp '$value'", e)
        null
    }
}

private fun formatHourLabel(millis: Long): String {
    val formatter = SimpleDateFormat("h a", Locale.US)
    formatter.timeZone = TimeZone.getTimeZone("America/New_York")
    return formatter.format(Date(millis))
}
