package kg.dev.shared.feature.player.library

import kg.dev.shared.core.common.media.MediaCatalogItem
import kg.dev.shared.core.common.media.MediaProviderId
import kg.dev.shared.core.common.media.MediaReference
import kg.dev.shared.core.storage.db.PlayerDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One row per provider-qualified reference; desired-state writes preserve the other saved flag. */
class SqlDelightSavedMediaRepository(
    private val database: PlayerDatabase,
    private val nowEpochMillis: () -> Long = ::savedMediaCurrentEpochMillis
) : SavedMediaRepository {
    private val favoriteState = MutableStateFlow(readFavorites())
    private val watchLaterState = MutableStateFlow(readWatchLater())
    private val itemStates = mutableMapOf<MediaReference, MutableStateFlow<SavedMediaState>>()

    override fun observe(reference: MediaReference): StateFlow<SavedMediaState> {
        return itemStates.getOrPut(reference) { MutableStateFlow(read(reference)?.state() ?: SavedMediaState()) }.asStateFlow()
    }

    override fun favorites(): StateFlow<List<SavedMedia>> = favoriteState.asStateFlow()
    override fun watchLater(): StateFlow<List<SavedMedia>> = watchLaterState.asStateFlow()

    override suspend fun setFavorite(item: MediaCatalogItem, enabled: Boolean) = update(item, favorite = enabled)
    override suspend fun setWatchLater(item: MediaCatalogItem, enabled: Boolean) = update(item, watchLater = enabled)

    private fun update(item: MediaCatalogItem, favorite: Boolean? = null, watchLater: Boolean? = null) {
        database.transaction {
            val existing = read(item.reference)
            val nextFavorite = favorite ?: existing?.isFavorite ?: false
            val nextWatchLater = watchLater ?: existing?.isWatchLater ?: false
            if (!nextFavorite && !nextWatchLater) {
                database.playerDatabaseQueries.deleteSavedMedia(item.reference.provider.value, item.reference.externalId)
            } else {
                val now = nowEpochMillis()
                database.playerDatabaseQueries.upsertSavedMedia(
                    providerId = item.reference.provider.value,
                    externalId = item.reference.externalId,
                    title = item.title,
                    thumbnailUrl = item.thumbnailUrl ?: existing?.thumbnailUrl,
                    authorTitle = item.authorTitle ?: existing?.authorTitle,
                    durationMs = item.durationMs ?: existing?.durationMs,
                    isFavorite = if (nextFavorite) 1 else 0,
                    isWatchLater = if (nextWatchLater) 1 else 0,
                    favoriteAddedAtEpochMs = when {
                        !nextFavorite -> null
                        existing?.isFavorite == true -> existing.favoriteAddedAtEpochMs
                        else -> now
                    },
                    watchLaterAddedAtEpochMs = when {
                        !nextWatchLater -> null
                        existing?.isWatchLater == true -> existing.watchLaterAddedAtEpochMs
                        else -> now
                    }
                )
            }
        }
        refresh(item.reference)
    }

    private fun refresh(reference: MediaReference) {
        favoriteState.value = readFavorites()
        watchLaterState.value = readWatchLater()
        itemStates[reference]?.value = read(reference)?.state() ?: SavedMediaState()
    }

    private fun read(reference: MediaReference): SavedMedia? =
        database.playerDatabaseQueries.selectSavedMedia(reference.provider.value, reference.externalId)
            .executeAsOneOrNull()?.toSavedMedia()

    private fun readFavorites(): List<SavedMedia> =
        database.playerDatabaseQueries.selectFavorites().executeAsList().map { it.toSavedMedia() }

    private fun readWatchLater(): List<SavedMedia> =
        database.playerDatabaseQueries.selectWatchLater().executeAsList().map { it.toSavedMedia() }
}

private fun kg.dev.shared.core.storage.SavedMedia.toSavedMedia() = SavedMedia(
    reference = MediaReference(MediaProviderId(providerId), externalId),
    title = title,
    thumbnailUrl = thumbnailUrl,
    authorTitle = authorTitle,
    durationMs = durationMs,
    isFavorite = isFavorite != 0L,
    isWatchLater = isWatchLater != 0L,
    favoriteAddedAtEpochMs = favoriteAddedAtEpochMs,
    watchLaterAddedAtEpochMs = watchLaterAddedAtEpochMs
)

private fun SavedMedia.state() = SavedMediaState(isFavorite, isWatchLater)

internal expect fun savedMediaCurrentEpochMillis(): Long
