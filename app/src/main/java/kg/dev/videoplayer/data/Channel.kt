package kg.dev.videoplayer.data

import kg.dev.common.network.api.response.Item
import kg.dev.common.utils.Mapper

data class Channel(
    val name: String,
    val description: String
)

class ChannelsMapper : Mapper<List<Item.Channel>, List<Channel>> {
    override fun convert(from: List<Item.Channel>): List<Channel> {
        return from.map { Channel(name = it.etag, description = it.kind) }
    }
}