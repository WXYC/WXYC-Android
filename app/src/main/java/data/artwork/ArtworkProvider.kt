package data.artwork

import data.Playcut

interface ArtworkProvider {
    suspend fun fetchImage(playcut: Playcut): String?
}
