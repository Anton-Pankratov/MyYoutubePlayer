package kg.dev.shared.feature.search.data.repository

import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import kg.dev.shared.core.common.Page
import kg.dev.shared.feature.search.data.remote.SearchHttpException
import kg.dev.shared.feature.search.data.remote.SearchRemoteDataSource
import kg.dev.shared.feature.search.domain.model.Channel
import kg.dev.shared.feature.search.domain.model.SearchError
import kg.dev.shared.feature.search.domain.model.SearchResult
import kg.dev.shared.feature.search.domain.repository.SearchRepository
import kg.dev.shared.feature.search.domain.model.ChannelVideosResult
import kg.dev.shared.core.common.media.MediaCatalogItem
import kg.dev.shared.core.common.media.MediaProviders
import kg.dev.shared.core.common.media.MediaReference
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import io.ktor.serialization.ContentConvertException
import kotlinx.io.IOException

internal class DefaultSearchRepository(
    private val remoteDataSource: SearchRemoteDataSource
) : SearchRepository {
    override suspend fun searchChannels(query: String, pageToken: String?): SearchResult = try {
        val response = remoteDataSource.searchChannels(query, pageToken)
        SearchResult.Success(
            Page(
                items = response.items.mapNotNull { item ->
                    val id = item.id.channelId ?: return@mapNotNull null
                    Channel(
                        id = id,
                        title = item.snippet.title,
                        description = item.snippet.description,
                        thumbnailUrl = item.snippet.thumbnails.medium?.url
                            ?: item.snippet.thumbnails.default?.url
                            ?: item.snippet.thumbnails.high?.url
                    )
                },
                nextPageToken = response.nextPageToken
            )
        )
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: SearchHttpException) {
        SearchResult.Failure(error.toSearchError())
    } catch (_: SerializationException) {
        SearchResult.Failure(SearchError.InvalidResponse)
    } catch (_: ContentConvertException) {
        SearchResult.Failure(SearchError.InvalidResponse)
    } catch (_: HttpRequestTimeoutException) {
        SearchResult.Failure(SearchError.Network)
    } catch (_: ResponseException) {
        SearchResult.Failure(SearchError.Network)
    } catch (_: IOException) {
        SearchResult.Failure(SearchError.Network)
    } catch (_: Throwable) {
        SearchResult.Failure(SearchError.Unknown)
    }

    override suspend fun channelVideos(channel: String, pageToken: String?): ChannelVideosResult = try {
        val response = remoteDataSource.channelVideos(channel, pageToken)
        ChannelVideosResult.Success(Page(
            items = response.items.mapNotNull { item ->
                val videoId = item.id.videoId ?: return@mapNotNull null
                MediaCatalogItem(
                    reference = MediaReference(MediaProviders.YouTube, videoId),
                    title = item.snippet.title,
                    thumbnailUrl = item.snippet.thumbnails.medium?.url
                        ?: item.snippet.thumbnails.default?.url
                        ?: item.snippet.thumbnails.high?.url,
                    authorTitle = item.snippet.channelTitle
                )
            },
            nextPageToken = response.nextPageToken
        ))
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: SearchHttpException) {
        ChannelVideosResult.Failure(error.toSearchError())
    } catch (_: SerializationException) {
        ChannelVideosResult.Failure(SearchError.InvalidResponse)
    } catch (_: ContentConvertException) {
        ChannelVideosResult.Failure(SearchError.InvalidResponse)
    } catch (_: HttpRequestTimeoutException) {
        ChannelVideosResult.Failure(SearchError.Network)
    } catch (_: ResponseException) {
        ChannelVideosResult.Failure(SearchError.Network)
    } catch (_: IOException) {
        ChannelVideosResult.Failure(SearchError.Network)
    } catch (_: Throwable) {
        ChannelVideosResult.Failure(SearchError.Unknown)
    }
}

private fun SearchHttpException.toSearchError(): SearchError = when {
    reason in setOf("quotaExceeded", "dailyLimitExceeded", "rateLimitExceeded") ->
        SearchError.QuotaExceeded
    status == HttpStatusCode.TooManyRequests -> SearchError.QuotaExceeded
    // YouTube reports an invalid/malformed API key as HTTP 400 (for example,
    // when the key is disabled or restricted for a different application).
    status == HttpStatusCode.BadRequest || status == HttpStatusCode.Unauthorized ->
        SearchError.Unauthorized
    status == HttpStatusCode.Forbidden -> SearchError.Unauthorized
    else -> SearchError.Network
}
