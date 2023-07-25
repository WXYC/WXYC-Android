package data

import com.google.gson.annotations.SerializedName

data class Pagination(
    val page: Int,
    val pages: Int,
    val perPage: Int,
    val items: Int,
    val urls: Urls
)

data class Urls(
    val last: String,
    val next: String
)

data class Result(
    val country: String,
    val year: String,
    val format: List<String>,
    val label: List<String>,
    val type: String,
    val genre: List<String>,
    val style: List<String>,
    val id: Int,
    val barcode: List<String>,
    val masterId: Int,
    val masterUrl: String,
    val uri: String,
    val catno: String,
    val title: String,
    val thumb: String,
    @SerializedName("cover_image")
    val coverImage: String?,
    val resourceUrl: String,
    val community: Community
)

data class Community(
    val want: Int,
    val have: Int
)

data class DiscogsResults(
    val pagination: Pagination,
    val results: List<Result>
)
