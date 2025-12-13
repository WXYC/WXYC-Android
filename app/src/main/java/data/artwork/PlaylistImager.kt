package data.artwork

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import data.DiscogsResults
import data.PlaylistDetails
import data.artwork.discogs.DiscogsArtFetcher
import data.artwork.discogs.DiscogsArtistsResults
import data.artwork.itunes.ITunesResults
import data.artwork.itunes.ItunesArtFetcher
import data.artwork.lastfm.LastFmArtFetcher
import data.artwork.lastfm.LastFmResults
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

// declare constants
private const val VARIOUS_ARTISTS = "v/a"
private const val VARIOUS_ARTISTS_FULL = "various artists"
private const val DISCOGS_API_KEY = "tYvsaskeJxOQbWoZSSkh"
private const val DISCOGS_SECRET_KEY = "vZuPZFFDerXIPrBfSNnNyDhXjpIUiyXi"
private const val LASTFM_METHOD = "album.getinfo"
private const val LASTFM_API_KEY = "45f85235ffc46cbb8769d545c8059399"


// fetches playlist image data. NEED TO CONCEAL THE KEY
class PlaylistImager {
    // fetches the image urls for given playlist using asynch tasks
    suspend fun fetchPlaylistImageURLs(playlist: MutableList<PlaylistDetails>): Unit =
        coroutineScope {
            val deferred = async {
                for (i in playlist.indices) {
                    // make sure it is a playcut
                    if (playlist[i].entryType != "playcut") {
                        continue
                    }
                    // Try fetching from lastfm
                    val lastFmURL = fetchLastFmImageURL(playlist[i])
                    if (lastFmURL != null) {
                        playlist[i].playcut.imageURL = lastFmURL
                        continue // Move on to the next iteration, no need to check Discogs/iTunes
                    }
                    // if lastfm null try iTunes
                    val itunesURL = fetchItunesImageURL(playlist[i])
                    if (itunesURL != null) {
                        playlist[i].playcut.imageURL = itunesURL
                        println("itunes saved the day")
                        continue
                    }
                    // if lastfm and itunes null try discogs
                    val discogsURL = fetchDiscogsImageURL(playlist[i])
                    if (discogsURL != null) {
                        playlist[i].playcut.imageURL = discogsURL
                        continue
                    }
                    // if all release searches fail, use artist image
                    val artistUrl = fetchDiscogsArtistImageURL(playlist[i])
                    if (artistUrl != null) {
                        playlist[i].playcut.imageURL = artistUrl
                        println("artist image filled in")
                        continue
                    }
                    println("All fetchers are null for " + playlist[i].playcut)
                }
            }
            return@coroutineScope deferred
        }.await()

    // fetches the image url from discogs
    suspend fun fetchDiscogsImageURL(playcut: PlaylistDetails): String? {
        try {
            val artist = playcut.playcut.artistName
            var release = playcut.playcut.releaseTitle
            if (release.equals("s/t", ignoreCase = true)) {
                release = playcut.playcut.artistName
            }
            val response = DiscogsArtFetcher.discogsService.getImage(
                artist,
                release,
                DISCOGS_API_KEY,
                DISCOGS_SECRET_KEY
            )
            if (response.isSuccessful) {
                val responseBody = response.body()
                if (responseBody != null) {
                    val discogsJson = responseBody.string()
                    val searchResult = Gson().fromJson(discogsJson, DiscogsResults::class.java)
                    var imageUrl: String? = null
                    for (result in searchResult.results) {
                        if (result.coverImage?.endsWith(".gif") == false) {
                            imageUrl = result.coverImage
                            break
                        }
                    }
                    if (imageUrl == "") {
                        return null
                    }
                    if (imageUrl != null) {
                        return imageUrl
                    }
                } else {
                    // Handle empty response body
                    println("discogs empty image data")
                }
            } else {
                // Handle the error response
                println("discogs art fetcher failed")
            }
        } catch (e: Exception) {
            // Handle network or other exceptions
        }
        return null
    }

    //fetches the image url from itunes
    suspend fun fetchItunesImageURL(playcut: PlaylistDetails): String? {
        try {
            val artist = playcut.playcut.artistName
            var release = playcut.playcut.releaseTitle
            if (release.equals("s/t", ignoreCase = true)) {
                release = playcut.playcut.artistName
            }
            val itunesSearch = "$artist $release"
            val response = ItunesArtFetcher.iTunesService.getImage(
                itunesSearch
            )
            if (response.isSuccessful) {
                val responseBody = response.body()
                if (responseBody != null) {
                    val itunesJson = responseBody.string()
                    val searchResult = Gson().fromJson(itunesJson, ITunesResults::class.java)
                    val imageUrl = searchResult.results.firstOrNull()?.artworkUrl100
                    if (imageUrl == "") {
                        return null
                    }
                    val imageUrlMaxSize = imageUrl?.replace("100x100bb", "1200x1200")
                    if (imageUrlMaxSize != null) {
                        return imageUrlMaxSize
                    }
                } else {
                    // Handle empty response body
                    println("itunes empty image data")
                }
            } else {
                // Handle the error response
                println("itunes art fetcher failed")
            }
        } catch (e: Exception) {
            // Handle network or other exceptions
        }
        return null
    }

    //fetches the image url from lastfm
    suspend fun fetchLastFmImageURL(playcut: PlaylistDetails): String? {
        try {
            val artist = playcut.playcut.artistName
            var release = playcut.playcut.releaseTitle
            if (release.equals("s/t", ignoreCase = true)) {
                release = playcut.playcut.artistName
            }
            val response = LastFmArtFetcher.lastFmService.getAlbumInfo(
                LASTFM_METHOD, LASTFM_API_KEY, artist,
                release, "json"
            )
            if (response.isSuccessful) {
                val responseBody = response.body()
                if (responseBody != null) {
                    val lastfmJson = responseBody.string()
                    val cleanedJson = lastfmJson.replace("\\/", "/")
                    val searchResult = Gson().fromJson(cleanedJson, LastFmResults::class.java)
                    val imageUrl = searchResult.album.image[4].text
                    if (imageUrl == "") {
                        return null
                    }

                    return imageUrl
                }
            } else {
                // Handle the error response
                println("LASTFM art fetcher failed")
            }
        } catch (e: Exception) {
            // Handle network or other exceptions
        }
        return null
    }

    // fetches the artist image url from discogs
    suspend fun fetchDiscogsArtistImageURL(playcut: PlaylistDetails): String? {
        val variousArtists = "Various Artists"
        try {
            var artist = playcut.playcut.artistName
            if (artist.equals(VARIOUS_ARTISTS, ignoreCase = true) || artist.equals(
                    VARIOUS_ARTISTS_FULL, ignoreCase = true
                )
            ) {
                artist = variousArtists
            }
            val response = DiscogsArtFetcher.discogsService.getArtist(
                artist,
                DISCOGS_API_KEY,
                DISCOGS_SECRET_KEY
            )
            if (response.isSuccessful) {
                //println("FETCH IMAGE CHECKPOINT 2")
                val responseBody = response.body()
                if (responseBody != null) {
                    val discogsJson = responseBody.string()
                    val searchResult =
                        Gson().fromJson(discogsJson, DiscogsArtistsResults::class.java)
                    val imageUrl = searchResult.results.firstOrNull()?.cover_image
                    if (imageUrl?.endsWith(".gif") == true) {
                        return null
                    }
                    if (imageUrl != null) {
                        return imageUrl
                    } else {
                        println("discogs no artist url")
                    }
                } else {
                    // Handle empty response body
                    println("empty image data")
                }
            } else {
                // Handle the error response
                println("discogs art fetcher failed")
            }
        } catch (e: Exception) {
            // Handle network or other exceptions
        }
        return null
    }
}