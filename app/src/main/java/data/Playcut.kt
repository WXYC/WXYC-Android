package data

data class Playcut(
    val id: Int,
    val entryType: String,
    val hour: Long,
    val chronOrderID: Int,
    val rotation: String? = null,
    val request: String? = null,
    val songTitle: String? = null,
    val labelName: String? = null,
    val artistName: String? = null,
    val releaseTitle: String? = null,
    var imageURL: String? = null
)
