package data.artwork

import android.util.Log
import data.PlaylistDetails
import data.artwork.discogs.DiscogsArtistArtworkProvider
import data.artwork.discogs.DiscogsArtworkProvider
import data.artwork.itunes.ItunesArtworkProvider
import data.artwork.lastfm.LastFmArtworkProvider
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

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
    suspend fun fetchPlaylistImageURLs(playlist: MutableList<PlaylistDetails>) {
        coroutineScope {
            playlist.filter { it.entryType == "playcut" }
                .map { playlistItem ->
                    async { fetchImageFor(playlistItem) }
                }.awaitAll()
        }
    }

    private suspend fun fetchImageFor(playlistItem: PlaylistDetails) {
        for (provider in providers) {
            Log.i("PlaylistImager", "Trying provider: ${provider.javaClass.simpleName}")
            val url = provider.fetchImage(playlistItem)
            if (url != null) {
                playlistItem.playcut.imageURL = url
                return
            }
        }
    }
}