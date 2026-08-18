package kg.dev.shared.feature.search.data.remote

import kotlinx.serialization.Serializable

@Serializable
internal data class SearchResponseDto(
    val nextPageToken: String? = null,
    val items: List<SearchItemDto> = emptyList()
)

@Serializable
internal data class SearchItemDto(
    val id: SearchItemIdDto = SearchItemIdDto(),
    val snippet: SearchSnippetDto = SearchSnippetDto()
)

@Serializable
internal data class SearchItemIdDto(val channelId: String? = null, val videoId: String? = null)

@Serializable
internal data class SearchSnippetDto(
    val title: String = "",
    val description: String = "",
    val channelTitle: String? = null,
    val thumbnails: SearchThumbnailsDto = SearchThumbnailsDto()
)

@Serializable
internal data class SearchThumbnailsDto(
    val default: SearchThumbnailDto? = null,
    val medium: SearchThumbnailDto? = null,
    val high: SearchThumbnailDto? = null
)

@Serializable
internal data class SearchThumbnailDto(val url: String? = null)
