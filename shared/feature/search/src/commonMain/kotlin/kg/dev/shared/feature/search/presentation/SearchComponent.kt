package kg.dev.shared.feature.search.presentation

import kg.dev.shared.feature.search.domain.model.Channel
import kg.dev.shared.feature.search.domain.model.SearchError
import kg.dev.shared.core.common.media.MediaCatalogItem
import kotlinx.coroutines.flow.StateFlow

data class SearchUiState(
    val query: String = DEFAULT_QUERY,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val items: List<Channel> = emptyList(),
    val selectedChannel: Channel? = null,
    val videos: List<MediaCatalogItem> = emptyList(),
    val nextPageToken: String? = null,
    val error: SearchError? = null
) {
    val canLoadMore: Boolean get() = nextPageToken != null && !isLoading && !isLoadingMore
}

interface SearchComponent {
    val state: StateFlow<SearchUiState>
    fun onQueryChanged(query: String)
    fun loadNextPage()
    fun retry()
    /** Loads catalog videos; a channel is not media and is never sent to playback. */
    fun selectChannel(channel: Channel)
    fun selectVideo(video: MediaCatalogItem)
    fun showChannels()
}

const val DEFAULT_QUERY = "Education"
