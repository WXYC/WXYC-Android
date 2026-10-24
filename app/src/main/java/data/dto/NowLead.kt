package data.dto

import org.wxyc.wxycapp.data.Playcut

enum class Lead { SONG, ENTRY }

/**
 * What the media notification and lock screen show. A null [artworkUrl] means the
 * station artwork, never the previous song's.
 */
data class NowMetadata(val lead: Lead, val title: String, val artist: String?, val artworkUrl: String?)

/**
 * The newer of the current song and the open show's who's-on entry, matching the iOS
 * app's freeform-show rule. [playcuts] is newest first. A `host` entry (no role line),
 * an entry from another show, and a null or 0 show id on either side never lead.
 */
fun nowLead(playcuts: List<Playcut>, onAir: OnAir): NowMetadata? {
    val song = playcuts.firstOrNull { it.entryType == "track" }
    val live = onAir as? OnAir.Live
    val showId = live?.showId?.takeIf { it != 0 }
    val entry = live?.spotlight?.toDomain()
    val roleLine = entry?.displayMessage
    val entryLeads = showId != null && entry != null && roleLine != null && entry.showId == showId &&
        (song == null || song.showId != showId || entry.playOrder > song.playOrder)

    return when {
        entryLeads -> NowMetadata(Lead.ENTRY, roleLine!!, live!!.djName, null)
        song != null -> NowMetadata(Lead.SONG, song.songTitle.orEmpty(), song.artistName, song.imageURL)
        else -> null
    }
}
