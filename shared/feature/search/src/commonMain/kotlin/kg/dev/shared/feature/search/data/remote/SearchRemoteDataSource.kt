package kg.dev.shared.feature.search.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kg.dev.shared.core.common.ApiConfigurationProvider
import kg.dev.shared.core.common.YOUTUBE_API_BASE_URL

internal interface SearchRemoteDataSource {
    suspend fun searchChannels(query: String, pageToken: String?): SearchResponseDto
    suspend fun channelVideos(channelId: String, pageToken: String?): SearchResponseDto
}

internal class KtorSearchRemoteDataSource(
    private val client: HttpClient,
    private val configuration: ApiConfigurationProvider
) : SearchRemoteDataSource {
    override suspend fun searchChannels(query: String, pageToken: String?): SearchResponseDto {
        return search(pageToken) {
            parameter("part", "snippet")
            parameter("type", "channel")
            parameter("q", query)
            parameter("order", "relevance")
        }
    }

    override suspend fun channelVideos(channelId: String, pageToken: String?): SearchResponseDto = search(pageToken) {
        parameter("part", "snippet")
        parameter("type", "video")
        parameter("channelId", channelId)
        parameter("order", "date")
    }

    private suspend fun search(
        pageToken: String?,
        configure: io.ktor.client.request.HttpRequestBuilder.() -> Unit
    ): SearchResponseDto {
        val response = client.get("$YOUTUBE_API_BASE_URL/search") {
            configure()
            parameter("pageToken", pageToken)
            parameter("maxResults", PAGE_SIZE)
            parameter("key", configuration.youtubeApiKey)
        }
        if (!response.status.isSuccess()) throw SearchHttpException(response.status)
        return response.body()
    }

    private companion object { const val PAGE_SIZE = 10 }
}

internal class SearchHttpException(val status: HttpStatusCode) : Exception()
