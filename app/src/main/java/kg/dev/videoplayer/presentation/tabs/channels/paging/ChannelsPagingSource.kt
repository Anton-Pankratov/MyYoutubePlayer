package kg.dev.videoplayer.presentation.tabs.channels.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import kg.dev.common.network.api.response.ApiResponse
import kg.dev.common.network.api.response.Item
import kg.dev.common.utils.safeCast
import kg.dev.videoplayer.data.ViewResponseData
import kg.dev.videoplayer.data.channel.ChannelViewData
import kg.dev.videoplayer.data.channel.ChannelsMapper

class ChannelsPagingSource(
    private val mapper: ChannelsMapper,
    private val loadChannels: (suspend (String?) -> ApiResponse<Item.Channel>)?
) : PagingSource<String, ChannelViewData>() {

    override suspend fun load(params: LoadParams<String>): LoadResult<String, ChannelViewData> {
        return try {
            loadChannels?.let { action ->
                val response = action.invoke(params.key)
                val data =
                    mapper.convert(response)
                        .safeCast<ViewResponseData.Success<List<ChannelViewData>>>()

                val newNextKey = response.nextPageToken.takeIf { it != params.key }

                LoadResult.Page(
                    data = data?.data ?: emptyList(),
                    prevKey = null,
                    nextKey = newNextKey
                )
            } ?: LoadResult.Error(NullPointerException())
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<String, ChannelViewData>): String? {
        return null
    }
}