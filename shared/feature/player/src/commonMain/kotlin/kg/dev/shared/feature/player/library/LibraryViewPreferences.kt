package kg.dev.shared.feature.player.library

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LibraryViewPreferences(
    val filter: SavedMediaFilter = SavedMediaFilter.All,
    val sort: SavedMediaSort = SavedMediaSort.RecentlySaved
)

interface LibraryViewPreferencesRepository {
    val preferences: StateFlow<LibraryViewPreferences>

    suspend fun setFilter(filter: SavedMediaFilter)
    suspend fun setSort(sort: SavedMediaSort)
}

/** Platform storage boundary; common Library logic never depends on platform preference APIs. */
interface LibraryViewPreferencesStorage {
    fun read(key: String): String?
    fun write(key: String, value: String)
}

class PersistentLibraryViewPreferencesRepository(
    private val storage: LibraryViewPreferencesStorage
) : LibraryViewPreferencesRepository {
    private val mutablePreferences = MutableStateFlow(
        LibraryViewPreferences(
            filter = storage.read(FILTER_KEY).toFilter(),
            sort = storage.read(SORT_KEY).toSort()
        )
    )
    override val preferences: StateFlow<LibraryViewPreferences> = mutablePreferences.asStateFlow()

    override suspend fun setFilter(filter: SavedMediaFilter) {
        storage.write(FILTER_KEY, filter.storageValue)
        mutablePreferences.value = mutablePreferences.value.copy(filter = filter)
    }

    override suspend fun setSort(sort: SavedMediaSort) {
        storage.write(SORT_KEY, sort.storageValue)
        mutablePreferences.value = mutablePreferences.value.copy(sort = sort)
    }

    private val SavedMediaFilter.storageValue: String
        get() = when (this) {
            SavedMediaFilter.All -> "all"
            SavedMediaFilter.Favorites -> "favorites"
            SavedMediaFilter.WatchLater -> "watch_later"
            SavedMediaFilter.Both -> "both"
        }

    private val SavedMediaSort.storageValue: String
        get() = when (this) {
            SavedMediaSort.RecentlySaved -> "recently_saved"
            SavedMediaSort.TitleAscending -> "title_asc"
            SavedMediaSort.TitleDescending -> "title_desc"
        }

    private fun String?.toFilter(): SavedMediaFilter = when (this) {
        "favorites" -> SavedMediaFilter.Favorites
        "watch_later" -> SavedMediaFilter.WatchLater
        "both" -> SavedMediaFilter.Both
        else -> SavedMediaFilter.All
    }

    private fun String?.toSort(): SavedMediaSort = when (this) {
        "title_asc" -> SavedMediaSort.TitleAscending
        "title_desc" -> SavedMediaSort.TitleDescending
        else -> SavedMediaSort.RecentlySaved
    }

    private companion object {
        const val FILTER_KEY = "library.view.filter"
        const val SORT_KEY = "library.view.sort"
    }
}
