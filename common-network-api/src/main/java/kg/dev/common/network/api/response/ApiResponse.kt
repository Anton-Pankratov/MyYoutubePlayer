package kg.dev.common.network.api.response

data class ApiResponse<Item>(
    val kind: String,
    val etag: String,
    val items: List<Item>
)