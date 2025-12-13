package data.artwork

import android.util.Log
import data.Playcut
import data.artwork.discogs.DiscogsArtistArtworkProvider
import data.artwork.discogs.DiscogsArtworkProvider
import data.artwork.itunes.ItunesArtworkProvider
import data.artwork.lastfm.LastFmArtworkProvider
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

// fetches playlist image data.
class PlaylistImager @Inject constructor(
    discogsProvider: DiscogsArtworkProvider,
    discogsArtistProvider: DiscogsArtistArtworkProvider,
    lastFmProvider: LastFmArtworkProvider,
    itunesProvider: ItunesArtworkProvider
) {
    // defined order of providers
    private val providers = listOf(
        discogsProvider,
//        discogsArtistProvider,
//        lastFmProvider,
//        itunesProvider
    )

    // fetches the image urls for given playlist using async tasks
    suspend fun fetchPlaylistImageURLs(playlist: MutableList<Playcut>) {
        withContext(Dispatchers.IO) {
            playlist.filter { it.entryType == "playcut" }
                .map { playlistItem ->
                    async { fetchImageFor(playlistItem) }
                }.awaitAll()
        }
    }

    private suspend fun fetchImageFor(playlistItem: Playcut) {
        for (provider in providers) {
            Log.i("PlaylistImager", "Trying provider: ${provider.javaClass.simpleName}")
            val url = provider.fetchImage(playlistItem)
            if (url != null) {
                playlistItem.imageURL = url
                return
            }
        }
    }
}
