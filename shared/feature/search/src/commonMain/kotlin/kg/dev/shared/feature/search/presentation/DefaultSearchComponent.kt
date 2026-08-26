package kg.dev.shared.feature.search.presentation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.Lifecycle
import kg.dev.shared.feature.search.domain.model.Channel
import kg.dev.shared.feature.search.domain.model.SearchResult
import kg.dev.shared.feature.search.domain.model.ChannelVideosResult
import kg.dev.shared.core.common.media.MediaCatalogItem
import kg.dev.shared.feature.search.domain.usecase.SearchChannelsUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

@OptIn(FlowPreview::class)
class DefaultSearchComponent(
    componentContext: ComponentContext,
    private val searchChannels: SearchChannelsUseCase,
    coroutineContext: CoroutineContext = Dispatchers.Default,
    debounceMillis: Long = 500,
    private val onMediaSelected: (MediaCatalogItem) -> Unit = {}
) : SearchComponent, ComponentContext by componentContext {
    private val scope = CoroutineScope(SupervisorJob() + coroutineContext)
    private val query = MutableStateFlow(DEFAULT_QUERY)
    private val mutableState = MutableStateFlow(SearchUiState())
    override val state: StateFlow<SearchUiState> = mutableState.asStateFlow()
    private var appendJob: Job? = null

    init {
        lifecycle.subscribe(object : Lifecycle.Callbacks {
            override fun onDestroy() {
                scope.cancel()
            }
        })
        scope.launch {
            query
                .map(String::trim)
                .debounce(debounceMillis)
                .distinctUntilChanged()
                .collectLatest(::loadFirstPage)
        }
    }

    override fun onQueryChanged(query: String) {
        mutableState.value = mutableState.value.copy(query = query, error = null)
        this.query.value = query
    }

    override fun loadNextPage() {
        val snapshot = mutableState.value
        val token = snapshot.nextPageToken ?: return
        if (snapshot.isLoading || snapshot.isLoadingMore || appendJob?.isActive == true) return
        appendJob = scope.launch {
            mutableState.value = mutableState.value.copy(isLoadingMore = true, error = null)
            if (snapshot.selectedChannel != null) {
                when (val result = searchChannels.channelVideos(snapshot.selectedChannel.id, token)) {
                    is ChannelVideosResult.Success -> mutableState.value = mutableState.value.copy(
                        isLoadingMore = false,
                        videos = (mutableState.value.videos + result.page.items)
                            .distinctBy { it.reference },
                        nextPageToken = result.page.nextPageToken
                    )
                    is ChannelVideosResult.Failure -> mutableState.value = mutableState.value.copy(isLoadingMore = false, error = result.error)
                }
            } else when (val result = searchChannels(snapshot.query, token)) {
                is SearchResult.Success -> mutableState.value = mutableState.value.copy(
                    isLoadingMore = false,
                    items = (mutableState.value.items + result.page.items)
                        .distinctBy { it.providerId to it.id },
                    nextPageToken = result.page.nextPageToken
                )
                is SearchResult.Failure -> mutableState.value = mutableState.value.copy(
                    isLoadingMore = false,
                    error = result.error
                )
            }
        }
    }

    override fun retry() {
        scope.launch { loadFirstPage(mutableState.value.query.trim()) }
    }

    override fun selectChannel(channel: Channel) {
        appendJob?.cancel()
        scope.launch { loadChannelVideos(channel) }
    }

    override fun selectVideo(video: MediaCatalogItem) = onMediaSelected(video)

    override fun showChannels() {
        mutableState.value = mutableState.value.copy(selectedChannel = null, videos = emptyList(), nextPageToken = null, error = null)
    }

    private suspend fun loadFirstPage(normalizedQuery: String) {
        appendJob?.cancel()
        mutableState.value = mutableState.value.copy(
            query = normalizedQuery,
            isLoading = true,
            isLoadingMore = false,
            items = emptyList(),
            selectedChannel = null,
            videos = emptyList(),
            nextPageToken = null,
            error = null
        )
        when (val result = searchChannels(normalizedQuery)) {
            is SearchResult.Success -> mutableState.value = mutableState.value.copy(
                isLoading = false,
                items = result.page.items.distinctBy { it.providerId to it.id },
                nextPageToken = result.page.nextPageToken
            )
            is SearchResult.Failure -> mutableState.value = mutableState.value.copy(
                isLoading = false,
                error = result.error
            )
        }
    }

    private suspend fun loadChannelVideos(channel: Channel) {
        mutableState.value = mutableState.value.copy(
            selectedChannel = channel, videos = emptyList(), isLoading = true, isLoadingMore = false,
            nextPageToken = null, error = null
        )
        when (val result = searchChannels.channelVideos(channel.id)) {
            is ChannelVideosResult.Success -> mutableState.value = mutableState.value.copy(
                isLoading = false,
                videos = result.page.items.distinctBy { it.reference },
                nextPageToken = result.page.nextPageToken
            )
            is ChannelVideosResult.Failure -> mutableState.value = mutableState.value.copy(isLoading = false, error = result.error)
        }
    }
}
