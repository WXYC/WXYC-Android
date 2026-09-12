package org.wxyc.wxycapp.data

data class Playcut(
    val id: Int,
    val entryType: String,
    val hour: Long,
    val showId: Int = 0,
    val playOrder: Int,
    val rotation: String? = null,
    val songTitle: String? = null,
    val labelName: String? = null,
    val artistName: String? = null,
    val releaseTitle: String? = null,
    val imageURL: String? = null,
    val metadata: PlaycutMetadata? = null
)
