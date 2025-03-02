package kg.dev.videoplayer.data.channel

import kg.dev.common.network.api.response.ApiResponse
import kg.dev.common.network.api.response.Item
import kg.dev.common.utils.Mapper
import kg.dev.videoplayer.data.ViewResponseData

class ChannelsMapper : Mapper<ApiResponse<Item.Channel>, ViewResponseData> {
    override fun convert(source: ApiResponse<Item.Channel>): ViewResponseData {
        return if (source.isSuccessful) {
            if (source.items.isNullOrEmpty()) {
                ViewResponseData.NoData()
            } else {
                ViewResponseData.Success(
                    data = source.items?.map {
                        val thumbnails = it.snippet.thumbnails
                        ChannelViewData(
                            title = it.snippet.title,
                            description = it.snippet.description,
                            thumbnail = ThumbnailViewData(
                                default = thumbnails.medium?.url,
                                medium = thumbnails.medium?.url,
                                high = thumbnails.high?.url
                            )
                        )
                    })
            }
        } else {
            ViewResponseData.Error(message = source.throwable?.message ?: "", code = -1)
        }
    }
}