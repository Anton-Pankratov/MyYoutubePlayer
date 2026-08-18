package kg.dev.shared.feature.search.domain.repository

import kg.dev.shared.feature.search.domain.model.SearchResult
import kg.dev.shared.feature.search.domain.model.ChannelVideosResult

interface SearchRepository {
    suspend fun searchChannels(query: String, pageToken: String? = null): SearchResult
    suspend fun channelVideos(channel: String, pageToken: String? = null): ChannelVideosResult =
        ChannelVideosResult.Failure(kg.dev.shared.feature.search.domain.model.SearchError.Unknown)
}
