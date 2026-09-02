package kg.dev.shared.feature.player.library

import kg.dev.shared.core.common.media.MediaCatalogItem
import kg.dev.shared.core.common.media.MediaProviderId
import kg.dev.shared.core.common.media.MediaReference
import kg.dev.shared.core.storage.db.PlayerDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.jvm.JvmInline
import kotlin.random.Random

@JvmInline
value class CollectionId(val value: String)

data class MediaCollection(
    val id: CollectionId,
    val name: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val itemCount: Long
)

data class CollectionMedia(
    val collectionId: CollectionId,
    val reference: MediaReference,
    val title: String,
    val thumbnailUrl: String?,
    val authorTitle: String?,
    val durationMs: Long?,
    val addedAtEpochMs: Long
) {
    fun toCatalogItem() = MediaCatalogItem(reference, title, thumbnailUrl, authorTitle, durationMs)
}

data class MediaCollectionDetail(val collection: MediaCollection, val items: List<CollectionMedia>)

fun interface CollectionIdGenerator { fun nextId(): CollectionId }

class RandomCollectionIdGenerator : CollectionIdGenerator {
    override fun nextId() = CollectionId("collection-${Random.nextLong().toString(16)}${Random.nextLong().toString(16)}")
}

interface MediaCollectionRepository {
    fun collections(): StateFlow<List<MediaCollection>>
    fun observeCollection(id: CollectionId): StateFlow<MediaCollectionDetail?>
    fun collectionIdsContaining(reference: MediaReference): StateFlow<Set<CollectionId>>
    suspend fun create(name: String): CollectionId
    suspend fun rename(id: CollectionId, name: String)
    suspend fun delete(id: CollectionId)
    suspend fun addMedia(id: CollectionId, media: MediaCatalogItem)
    suspend fun removeMedia(id: CollectionId, reference: MediaReference)
}

class SqlDelightMediaCollectionRepository(
    private val database: PlayerDatabase,
    private val idGenerator: CollectionIdGenerator = RandomCollectionIdGenerator(),
    private val nowEpochMillis: () -> Long = ::collectionCurrentEpochMillis
) : MediaCollectionRepository {
    private val collectionState = MutableStateFlow(readCollections())
    private val details = mutableMapOf<CollectionId, MutableStateFlow<MediaCollectionDetail?>>()
    private val memberships = mutableMapOf<MediaReference, MutableStateFlow<Set<CollectionId>>>()

    override fun collections(): StateFlow<List<MediaCollection>> = collectionState.asStateFlow()
    override fun observeCollection(id: CollectionId): StateFlow<MediaCollectionDetail?> =
        details.getOrPut(id) { MutableStateFlow(readDetail(id)) }.asStateFlow()
    override fun collectionIdsContaining(reference: MediaReference): StateFlow<Set<CollectionId>> =
        memberships.getOrPut(reference) { MutableStateFlow(readMemberships(reference)) }.asStateFlow()

    override suspend fun create(name: String): CollectionId {
        val validName = name.validatedName()
        val id = idGenerator.nextId()
        val now = nowEpochMillis()
        database.playerDatabaseQueries.insertCollection(id.value, validName, now, now)
        refresh()
        return id
    }

    override suspend fun rename(id: CollectionId, name: String) {
        requireCollection(id)
        database.playerDatabaseQueries.updateCollectionName(name.validatedName(), nowEpochMillis(), id.value)
        refresh()
    }

    override suspend fun delete(id: CollectionId) {
        if (readCollection(id) == null) return
        database.transaction {
            database.playerDatabaseQueries.deleteCollectionMemberships(id.value)
            database.playerDatabaseQueries.deleteCollection(id.value)
        }
        refresh()
    }

    override suspend fun addMedia(id: CollectionId, media: MediaCatalogItem) {
        requireCollection(id)
        if (readMemberships(media.reference).contains(id)) return
        database.transaction {
            val now = nowEpochMillis()
            database.playerDatabaseQueries.insertCollectionMedia(
                id.value, media.reference.provider.value, media.reference.externalId, media.title,
                media.thumbnailUrl, media.authorTitle, media.durationMs, now
            )
            database.playerDatabaseQueries.updateCollectionTimestamp(now, id.value)
        }
        refresh()
    }

    override suspend fun removeMedia(id: CollectionId, reference: MediaReference) {
        requireCollection(id)
        if (!readMemberships(reference).contains(id)) return
        database.transaction {
            database.playerDatabaseQueries.deleteCollectionMedia(id.value, reference.provider.value, reference.externalId)
            database.playerDatabaseQueries.updateCollectionTimestamp(nowEpochMillis(), id.value)
        }
        refresh()
    }

    private fun requireCollection(id: CollectionId) {
        requireNotNull(readCollection(id)) { "Collection ${id.value} does not exist" }
    }

    private fun refresh() {
        collectionState.value = readCollections()
        details.forEach { (id, state) -> state.value = readDetail(id) }
        memberships.forEach { (reference, state) -> state.value = readMemberships(reference) }
    }

    private fun readCollections(): List<MediaCollection> =
        database.playerDatabaseQueries.selectCollections().executeAsList().map {
            MediaCollection(CollectionId(it.id), it.name, it.createdAtEpochMs, it.updatedAtEpochMs, it.itemCount)
        }
    private fun readCollection(id: CollectionId): MediaCollection? =
        database.playerDatabaseQueries.selectCollection(id.value).executeAsOneOrNull()?.let {
            MediaCollection(CollectionId(it.id), it.name, it.createdAtEpochMs, it.updatedAtEpochMs,
                database.playerDatabaseQueries.selectCollectionItems(id.value).executeAsList().size.toLong())
        }
    private fun readDetail(id: CollectionId): MediaCollectionDetail? = readCollection(id)?.let { collection ->
        MediaCollectionDetail(collection, database.playerDatabaseQueries.selectCollectionItems(id.value).executeAsList().map {
            CollectionMedia(CollectionId(it.collectionId), MediaReference(MediaProviderId(it.providerId), it.externalId),
                it.title, it.thumbnailUrl, it.authorTitle, it.durationMs, it.addedAtEpochMs)
        })
    }
    private fun readMemberships(reference: MediaReference): Set<CollectionId> =
        database.playerDatabaseQueries.selectCollectionIdsContaining(reference.provider.value, reference.externalId)
            .executeAsList().map(::CollectionId).toSet()
}

private fun String.validatedName(): String = trim().also {
    require(it.isNotEmpty()) { "Collection name cannot be blank" }
    require(it.length <= 100) { "Collection name must be at most 100 characters" }
}

internal expect fun collectionCurrentEpochMillis(): Long
