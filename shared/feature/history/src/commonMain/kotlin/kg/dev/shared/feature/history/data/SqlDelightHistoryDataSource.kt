package kg.dev.shared.feature.history.data

import kg.dev.shared.core.storage.db.PlayerDatabase
import kg.dev.shared.feature.history.domain.WatchedVideo

internal class SqlDelightHistoryDataSource(private val database: PlayerDatabase) {
    fun upsert(video: WatchedVideo) {
        database.playerDatabaseQueries.upsertHistory(
            providerId = video.reference.provider.value,
            externalId = video.reference.externalId,
            title = video.title,
            thumbnailUrl = video.thumbnailUrl,
            positionMs = video.positionMs,
            durationMs = video.durationMs,
            updatedAtEpochMs = video.watchedAtEpochMs
        )
    }

    fun recent(limit: Long): List<WatchedVideo> =
        database.playerDatabaseQueries.selectRecent(limit).executeAsList().map { row ->
            WatchedVideo(
                reference = kg.dev.shared.core.common.media.MediaReference(
                    kg.dev.shared.core.common.media.MediaProviderId(row.providerId), row.externalId
                ),
                title = row.title,
                thumbnailUrl = row.thumbnailUrl,
                positionMs = row.positionMs,
                durationMs = row.durationMs,
                watchedAtEpochMs = row.updatedAtEpochMs
            )
        }

    fun delete(reference: kg.dev.shared.core.common.media.MediaReference) {
        database.playerDatabaseQueries.deleteHistory(reference.provider.value, reference.externalId)
    }
}
