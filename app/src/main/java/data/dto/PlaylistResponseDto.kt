package data.dto

import data.Playcut

data class PlaylistResponseDto(
    val id: Int,
    val entryType: String,
    val playcut: PlayCutDetailsDto?,
    val hour: Long,
    val chronOrderID: Int
) {
    fun toDomain(): Playcut {
        return Playcut(
            id = id,
            entryType = entryType,
            hour = hour,
            chronOrderID = chronOrderID,
            rotation = playcut?.rotation,
            request = playcut?.request,
            songTitle = playcut?.songTitle,
            labelName = playcut?.labelName,
            artistName = playcut?.artistName,
            releaseTitle = playcut?.releaseTitle,
            imageURL = playcut?.imageURL
        )
    }
}

data class PlayCutDetailsDto(
    val rotation: String,
    val request: String,
    val songTitle: String,
    val labelName: String,
    val artistName: String,
    val releaseTitle: String,
    var imageURL: String
)
