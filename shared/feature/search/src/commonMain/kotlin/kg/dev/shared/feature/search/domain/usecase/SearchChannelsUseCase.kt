package kg.dev.shared.feature.search.domain.usecase

import kg.dev.shared.core.common.Page
import kg.dev.shared.feature.search.domain.model.Channel
import kg.dev.shared.feature.search.domain.model.SearchResult
import kg.dev.shared.feature.search.domain.model.ChannelVideosResult
import kg.dev.shared.feature.search.domain.repository.SearchRepository

class SearchChannelsUseCase(private val repository: SearchRepository) {
    suspend operator fun invoke(query: String, pageToken: String? = null): SearchResult {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) {
            return SearchResult.Success(Page<Channel>(emptyList(), null))
        }
        return repository.searchChannels(normalizedQuery, pageToken)
    }

    suspend fun channelVideos(channelId: String, pageToken: String? = null): ChannelVideosResult =
        repository.channelVideos(channelId, pageToken)
}
