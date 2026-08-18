package kg.dev.shared.core.common

data class Page<T>(
    val items: List<T>,
    val nextPageToken: String?
)
