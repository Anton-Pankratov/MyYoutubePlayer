package kg.dev.shared.feature.search.domain.model

import kg.dev.shared.core.common.Page
import kg.dev.shared.core.common.media.MediaCatalogItem

sealed interface SearchResult {
    data class Success(val page: Page<Channel>) : SearchResult
    data class Failure(val error: SearchError) : SearchResult
}

sealed interface ChannelVideosResult {
    data class Success(val page: Page<MediaCatalogItem>) : ChannelVideosResult
    data class Failure(val error: SearchError) : ChannelVideosResult
}

enum class SearchError {
    Network,
    Unauthorized,
    QuotaExceeded,
    InvalidResponse,
    Unknown
}
