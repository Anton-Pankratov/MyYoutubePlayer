package kg.dev.shared.feature.player.library

import kg.dev.shared.core.common.media.MediaCatalogItem
import kg.dev.shared.core.common.media.MediaReference

/** Durable, provider-neutral user-library state. Display metadata is a cached catalog snapshot. */
data class SavedMedia(
    val reference: MediaReference,
    val title: String,
    val thumbnailUrl: String?,
    val authorTitle: String?,
    val durationMs: Long?,
    val isFavorite: Boolean,
    val isWatchLater: Boolean,
    val favoriteAddedAtEpochMs: Long?,
    val watchLaterAddedAtEpochMs: Long?
) {
    fun toCatalogItem() = MediaCatalogItem(reference, title, thumbnailUrl, authorTitle, durationMs)
}

data class SavedMediaState(
    val isFavorite: Boolean = false,
    val isWatchLater: Boolean = false
)

interface SavedMediaRepository {
    fun observe(reference: MediaReference): kotlinx.coroutines.flow.StateFlow<SavedMediaState>
    fun favorites(): kotlinx.coroutines.flow.StateFlow<List<SavedMedia>>
    fun watchLater(): kotlinx.coroutines.flow.StateFlow<List<SavedMedia>>
    fun observeFavorites(): kotlinx.coroutines.flow.Flow<List<SavedMedia>> = favorites()
    fun observeWatchLater(): kotlinx.coroutines.flow.Flow<List<SavedMedia>> = watchLater()
    suspend fun setFavorite(item: MediaCatalogItem, enabled: Boolean)
    suspend fun setWatchLater(item: MediaCatalogItem, enabled: Boolean)
}
