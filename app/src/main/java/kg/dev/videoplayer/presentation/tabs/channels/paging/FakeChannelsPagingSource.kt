package kg.dev.videoplayer.presentation.tabs.channels.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import kg.dev.videoplayer.data.channel.ChannelViewData
import kotlinx.coroutines.delay

class FakeChannelsPagingSource(
    private val fakeData: List<ChannelViewData>
) : PagingSource<String, ChannelViewData>() {

    override suspend fun load(params: LoadParams<String>): LoadResult<String, ChannelViewData> {
        delay(1500)
        return LoadResult.Page(
            data = fakeData,
            prevKey = null,
            nextKey = null // Указываем, что это последняя страница
        )
    }

    override fun getRefreshKey(state: PagingState<String, ChannelViewData>): String? {
        return null
    }
}