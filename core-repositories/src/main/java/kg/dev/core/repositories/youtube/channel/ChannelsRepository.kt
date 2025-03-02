package kg.dev.core.repositories.youtube.channel

import kg.dev.common.network.api.response.ApiResponse
import kg.dev.common.network.api.response.Item

interface ChannelsRepository {

    suspend fun findChannels(query: String, nextPageToken: String?): ApiResponse<Item.Channel>
}