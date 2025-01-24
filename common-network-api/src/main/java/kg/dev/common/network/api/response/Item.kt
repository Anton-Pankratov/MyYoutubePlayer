package kg.dev.common.network.api.response

sealed class Item(open val kind: String, open val snippet: Snippet) {

    data class Channel(
        override val kind: String,
        override val snippet: Snippet,
        val etag: String,
        val id: ItemId,
        val statistic: Statistics
    ) : Item(kind, snippet)
}