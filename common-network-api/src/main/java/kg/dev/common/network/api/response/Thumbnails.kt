package kg.dev.common.network.api.response

data class Thumbnails(
    val default: ThumbnailDetails?,
    val medium: ThumbnailDetails?,
    val high: ThumbnailDetails?
)