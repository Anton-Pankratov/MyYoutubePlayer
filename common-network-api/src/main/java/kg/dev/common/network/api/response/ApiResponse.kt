package kg.dev.common.network.api.response

data class ApiResponse<Item>(
    val kind: String? = null,
    val etag: String? = null,
    val nextPageToken: String? = null,
    val regionCode: String? = null,
    val pageInfo: PageInfo? = null,
    val items: List<Item>? = null,
    val throwable: Throwable? = null,
    val isSuccessful: Boolean
) {

    companion object {

        fun <Item> success(
            kind: String?,
            etag: String?,
            nextPageToken: String?,
            regionCode: String?,
            pageInfo: PageInfo?,
            items: List<Item>?
        ): ApiResponse<Item> {
            return ApiResponse(
                kind,
                etag,
                nextPageToken,
                regionCode,
                pageInfo,
                items,
                isSuccessful = true
            )
        }

        fun <Item> error(e: Throwable?): ApiResponse<Item> {
            return ApiResponse(throwable = e, isSuccessful = false)
        }
    }
}