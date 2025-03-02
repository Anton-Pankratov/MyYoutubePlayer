package kg.dev.common.network.api.response

sealed class Item {

    data class Channel(
        val kind: String,
        val snippet: Snippet.Channel,
        val etag: String,
        val id: ItemId,
    ) : Item()
}