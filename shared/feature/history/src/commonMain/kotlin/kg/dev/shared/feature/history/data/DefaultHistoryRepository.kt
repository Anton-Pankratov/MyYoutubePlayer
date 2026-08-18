package kg.dev.shared.feature.history.data

import kg.dev.shared.feature.history.domain.HistoryRepository
import kg.dev.shared.feature.history.domain.WatchedVideo
import kg.dev.shared.core.common.media.MediaReference

internal class DefaultHistoryRepository(
    private val dataSource: SqlDelightHistoryDataSource
) : HistoryRepository {
    override suspend fun save(video: WatchedVideo) = dataSource.upsert(video)
    override suspend fun recent(limit: Long): List<WatchedVideo> = dataSource.recent(limit)
    override suspend fun delete(reference: MediaReference) = dataSource.delete(reference)
}
