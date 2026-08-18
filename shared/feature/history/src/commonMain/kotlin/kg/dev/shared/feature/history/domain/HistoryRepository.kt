package kg.dev.shared.feature.history.domain

import kg.dev.shared.core.common.media.MediaReference

interface HistoryRepository {
    suspend fun save(video: WatchedVideo)
    suspend fun recent(limit: Long = 100): List<WatchedVideo>
    suspend fun delete(reference: MediaReference)
}
