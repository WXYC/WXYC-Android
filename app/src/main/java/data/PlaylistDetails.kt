package data

data class PlaylistDetails(
    val id: Int,
    val entryType: String,
    val playcut: PlayCutDetails,
    val hour: Long,
    val chronOrderID: Int

)

data class PlayCutDetails(
    val rotation: String,
    val request: String,
    val songTitle: String,
    val labelName: String,
    val artistName: String,
    val releaseTitle: String,
    var imageURL: String
)



