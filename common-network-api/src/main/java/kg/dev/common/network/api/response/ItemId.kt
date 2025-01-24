package kg.dev.common.network.api.response

data class ItemId(
    val kind: String,
    val videoId: String? = null,
    val channelId: String? = null,
    val playlistId: String? = null
)