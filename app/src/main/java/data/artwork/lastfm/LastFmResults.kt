package data.artwork.lastfm

import com.google.gson.annotations.SerializedName

data class LastFmResults(
    val album: Album
)

data class Album(
    val artist: String,
    val mbid: String,
    val tags: Any?, // Since the "tags" field in the JSON is empty, you can keep it as a String here
    val name: String,
    val image: List<Image>,
    val tracks: Tracks,
    val listeners: String,


    val playcount: String,
    val url: String
)

data class TagsList(
    @SerializedName("tag")
    val tag: Any?
)

data class TagItem(
    val url: String,
    @SerializedName("#text")
    val name: String
)

data class Image(
    val size: String,
    @SerializedName("#text")
    val text: String
)

data class Tracks(
    val track: List<Track>
)

data class Track(
    val streamable: Streamable,
    val duration: Int, // Assuming the "duration" field in the JSON is always an integer
    val url: String,
    val name: String,
    @SerializedName("@attr")
    val attr: Attr,
    val artist: Artist
)

data class Streamable(
    val fulltrack: String,
    @SerializedName("#text")
    val text: String
)

data class Attr(
    val rank: Int
)

data class Artist(
    val url: String,
    val name: String,
    val mbid: String
)

