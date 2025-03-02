package kg.dev.common.network.api.response

sealed class Snippet {

    data class Channel(
        val title: String,
        val thumbnails: Thumbnails,
        val description: String,
        val publishedAt: String,
        val channelId: String,
        val liveBroadCastContent: String,
        val publishTime: String
        ) : Snippet()
}