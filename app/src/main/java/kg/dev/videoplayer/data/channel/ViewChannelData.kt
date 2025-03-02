package kg.dev.videoplayer.data.channel

data class ChannelViewData(
    val title: String,
    val description: String,
    val thumbnail: ThumbnailViewData
)

data class ThumbnailViewData(
    val default: String?,
    val medium: String?,
    val high: String?
)