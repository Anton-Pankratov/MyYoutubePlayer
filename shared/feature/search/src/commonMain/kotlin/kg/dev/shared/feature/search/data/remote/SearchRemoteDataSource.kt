package kg.dev.shared.feature.search.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
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
            // Do not send a literal/null pageToken on the first page. YouTube
            // treats an invalid page token as a bad request.
            pageToken?.let { parameter("pageToken", it) }
            parameter("maxResults", PAGE_SIZE)
            parameter("key", configuration.youtubeApiKey)
        }
        if (!response.status.isSuccess()) {
            // YouTube uses the same HTTP status (especially 403) for very
            // different failures. Preserve its machine-readable reason so
            // the UI does not report an Android-key restriction as a quota
            // exhaustion error.
            val errorBody = response.bodyAsText()
            val reason = extractYouTubeErrorReason(errorBody)
            throw SearchHttpException(response.status, reason)
        }
        return response.body()
    }

    private companion object {
        const val PAGE_SIZE = 10
    }
}

internal fun extractYouTubeErrorReason(errorBody: String): String? {
    val key = "\"reason\""
    var cursor = errorBody.indexOf(key)
    if (cursor < 0) return null
    cursor += key.length
    while (cursor < errorBody.length && errorBody[cursor].isWhitespace()) cursor++
    if (cursor >= errorBody.length || errorBody[cursor] != ':') return null
    cursor++
    while (cursor < errorBody.length && errorBody[cursor].isWhitespace()) cursor++
    if (cursor >= errorBody.length || errorBody[cursor] != '"') return null
    val valueStart = cursor + 1
    val valueEnd = errorBody.indexOf('"', valueStart)
    if (valueEnd <= valueStart) return null
    return errorBody.substring(valueStart, valueEnd)
}

internal class SearchHttpException(
    val status: HttpStatusCode,
    val reason: String? = null
) : Exception()
