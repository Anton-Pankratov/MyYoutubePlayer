package kg.dev.core.repositories.youtube

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kg.dev.common.network.api.response.Item
import kg.dev.common.network.services.YoutubeService
import kg.dev.common.network.services.handleApiResponse

class ChannelsRepositoryImpl(private val youtubeService: YoutubeService, private val gson: Gson) :
    ChannelsRepository {

    override suspend fun findChannels(query: String): List<Item.Channel>? {
        val result = handleApiResponse(
            youtubeService.search(
                type = "channel",
                query = query
            )
        ) { responseBody ->
            val type = object : TypeToken<List<Item.Channel>>() {}.type
            gson.fromJson<List<Item.Channel>>(responseBody, type)
        }
        return if (result.isSuccessful) {
            result.data
        } else null
    }
}