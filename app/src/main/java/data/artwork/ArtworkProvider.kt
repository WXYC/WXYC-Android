package data.artwork

import org.wxyc.wxycapp.data.Playcut

interface ArtworkProvider {
    suspend fun fetchImage(playcut: Playcut): String?
}
