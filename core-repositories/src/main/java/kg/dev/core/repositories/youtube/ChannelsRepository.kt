package kg.dev.core.repositories.youtube

import kg.dev.common.network.api.response.Item

interface ChannelsRepository {

    suspend fun findChannels(query: String): List<Item.Channel>?
}