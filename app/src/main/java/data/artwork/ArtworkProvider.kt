package data.artwork

import data.PlaylistDetails

interface ArtworkProvider {
    suspend fun fetchImage(playcut: PlaylistDetails): String?
}
