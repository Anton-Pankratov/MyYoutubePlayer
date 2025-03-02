package kg.dev.videoplayer.domain.channel

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kg.dev.common.usecase.UseCase
import kg.dev.core.repositories.youtube.channel.ChannelsRepository
import kg.dev.videoplayer.data.channel.ChannelViewData
import kg.dev.videoplayer.data.channel.ChannelsMapper
import kg.dev.videoplayer.data.events.AppEvents.Channel.CallNextPage
import kg.dev.videoplayer.presentation.tabs.channels.paging.ChannelsPagingSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest

class SearchChannelsUseCase(
    private val repository: ChannelsRepository,
    private val mapper: ChannelsMapper,
    override val coroutineScope: CoroutineScope
) : UseCase(coroutineScope) {

    private val _searchQuery = MutableStateFlow(DEFAULT_SEARCH_QUERY)
    val searchQuery: StateFlow<String> = _searchQuery

    private val _pageLoad = MutableStateFlow<CallNextPage>(CallNextPage.Loading)
    val pageLoad: StateFlow<CallNextPage> = _pageLoad

    @OptIn(ExperimentalCoroutinesApi::class)
    val channels: Flow<PagingData<ChannelViewData>> =
        _searchQuery.flatMapLatest { query ->
            Pager(
                config = PagingConfig(pageSize = 10),
                pagingSourceFactory = {
                    ChannelsPagingSource(mapper) { token ->
                        repository.findChannels(
                            query = query,
                            nextPageToken = token
                        )
                    }
                }
            ).flow.distinctUntilChanged().cachedIn(coroutineScope)
        }

    fun findChannels(query: String?) {
        _searchQuery.value = query ?: DEFAULT_SEARCH_QUERY
    }

    private companion object {

        const val DEFAULT_SEARCH_QUERY = "Education"
    }
}