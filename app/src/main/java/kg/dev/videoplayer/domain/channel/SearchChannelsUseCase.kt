package kg.dev.videoplayer.domain.channel

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kg.dev.common.network.api.response.ApiResponse
import kg.dev.common.network.api.response.Item
import kg.dev.common.usecase.UseCase
import kg.dev.core.repositories.youtube.channel.ChannelsRepository
import kg.dev.videoplayer.data.channel.ChannelViewData
import kg.dev.videoplayer.data.channel.ChannelsMapper
import kg.dev.videoplayer.data.channel.ThumbnailViewData
import kg.dev.videoplayer.presentation.tabs.channels.paging.ChannelsPagingSource
import kg.dev.videoplayer.presentation.tabs.channels.paging.FakeChannelsPagingSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest

class SearchChannelsUseCase(
    private val repository: ChannelsRepository,
    private val mapper: ChannelsMapper,
) : UseCase() {

    private val _searchQuery = MutableStateFlow(DEFAULT_SEARCH_QUERY)

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    fun getChannels(coroutineScope: CoroutineScope): Flow<PagingData<ChannelViewData>> {
        return _searchQuery
            .debounce(1000)
            .distinctUntilChanged()
            .flatMapLatest { query ->
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
                ).flow.cachedIn(coroutineScope)
            }
    }

    fun getFakeChannelsFlow(coroutineScope: CoroutineScope): Flow<PagingData<ChannelViewData>> {
        val fakeData = listOf(
            ChannelViewData(
                title = "Tech Insider",
                description = "Latest tech news and reviews",
                thumbnail = ThumbnailViewData(
                    default = "https://example.com/tech_insider_default.jpg",
                    medium = "https://example.com/tech_insider_medium.jpg",
                    high = "https://example.com/tech_insider_high.jpg"
                )
            ),
            ChannelViewData(
                title = "Gaming Hub",
                description = "Daily gaming highlights",
                thumbnail = ThumbnailViewData(
                    default = "https://example.com/gaming_hub_default.jpg",
                    medium = "https://example.com/gaming_hub_medium.jpg",
                    high = "https://example.com/gaming_hub_high.jpg"
                )
            ),
            ChannelViewData(
                title = "Music Vibes",
                description = "Top music videos and playlists",
                thumbnail = ThumbnailViewData(
                    default = "https://example.com/music_vibes_default.jpg",
                    medium = "https://example.com/music_vibes_medium.jpg",
                    high = "https://example.com/music_vibes_high.jpg"
                )
            )
        )

        return Pager(
            config = PagingConfig(pageSize = 10),
            pagingSourceFactory = { FakeChannelsPagingSource(fakeData) }
        ).flow.distinctUntilChanged().cachedIn(coroutineScope)
    }

    private companion object {

        const val DEFAULT_SEARCH_QUERY = "Education"
    }
}