package data.artwork.discogs

data class DiscogsArtistsResults(val pagination: Pagination,
                                 val results: List<Result>
)

data class Pagination(
    val page: Int,
    val pages: Int,
    val per_page: Int,
    val items: Int,
    val urls: Map<String, String>
)

data class Result(
    val id: Long,
    val type: String,
    val master_id: Long?,
    val master_url: String?,
    val uri: String,
    val title: String,
    val thumb: String,
    val cover_image: String,
    val resource_url: String
)
