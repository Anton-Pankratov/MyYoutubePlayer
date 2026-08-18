package kg.dev.shared.feature.player

import kg.dev.shared.core.storage.db.PlayerDatabase

data class StoredDirectMedia(val externalId: String, val title: String, val uri: String, val mimeType: String? = null, val thumbnailUrl: String? = null, val authorTitle: String? = null, val durationMs: Long? = null)

interface DirectMediaSourceStore {
    suspend fun upsert(source: StoredDirectMedia)
    suspend fun find(externalId: String): StoredDirectMedia?
    suspend fun delete(externalId: String)
}

class SqlDelightDirectMediaSourceStore(private val database: PlayerDatabase) : DirectMediaSourceStore {
    override suspend fun upsert(source: StoredDirectMedia) = database.playerDatabaseQueries.upsertDirectMedia(source.externalId, source.title, source.uri, source.mimeType, source.thumbnailUrl, source.authorTitle, source.durationMs)
    override suspend fun find(externalId: String) = database.playerDatabaseQueries.findDirectMedia(externalId).executeAsOneOrNull()?.let { StoredDirectMedia(it.externalId, it.title, it.uri, it.mimeType, it.thumbnailUrl, it.authorTitle, it.durationMs) }
    override suspend fun delete(externalId: String) { database.playerDatabaseQueries.deleteDirectMedia(externalId) }
}
