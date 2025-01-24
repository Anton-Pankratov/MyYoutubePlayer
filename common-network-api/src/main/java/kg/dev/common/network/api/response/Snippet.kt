package kg.dev.common.network.api.response

sealed class Snippet(open val title: String, open val description: String, open val thumbnails: Thumbnails) {

    data class Channel(
        override val title: String,
        override val description: String,
        override val thumbnails: Thumbnails
        ) : Snippet(title, description, thumbnails)
}