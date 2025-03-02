package kg.dev.core.repositories.youtube.channel

import kg.dev.common.network.api.response.ApiResponse
import kg.dev.common.network.api.response.Item
import kg.dev.common.network.services.ResponseBodyMapper
import kg.dev.common.network.services.YoutubeService
import org.koin.core.component.KoinComponent

class ChannelsRepositoryImpl(
    private val youtubeService: YoutubeService
) : ChannelsRepository, KoinComponent {

    private val responseBodyMapper: ResponseBodyMapper<ApiResponse<*>> = ResponseBodyMapper(ApiResponse::class.java)
    private val serviceResponseMapper: ServiceResponseMapper<Item.Channel> = ServiceResponseMapper(Item.Channel::class.java)

    override suspend fun findChannels(query: String, nextPageToken: String?): ApiResponse<Item.Channel> {
        val serviceResponse = responseBodyMapper.convert(
            youtubeService.search(
                type = "channel",
                query = query,
                nextPageToken = nextPageToken
            )
        )
        return serviceResponseMapper.convert(serviceResponse)
    }
}