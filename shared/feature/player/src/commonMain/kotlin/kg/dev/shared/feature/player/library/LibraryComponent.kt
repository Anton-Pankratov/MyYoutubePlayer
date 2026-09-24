package kg.dev.shared.feature.player.library

import com.arkivanov.decompose.ComponentContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data class Content(
        val favorites: List<SavedMedia>,
        val watchLater: List<SavedMedia>,
        val searchQuery: String = "",
        val filter: SavedMediaFilter = SavedMediaFilter.All,
        val sort: SavedMediaSort = SavedMediaSort.RecentlySaved,
        val hasAnySavedMedia: Boolean = favorites.isNotEmpty() || watchLater.isNotEmpty(),
        val showFavorites: Boolean = true,
        val showWatchLater: Boolean = true
    ) : LibraryUiState
    data object Error : LibraryUiState
}

enum class SavedMediaFilter { All, Favorites, WatchLater, Both }
enum class SavedMediaSort { RecentlySaved, TitleAscending, TitleDescending }

interface LibraryComponent {
    val state: StateFlow<LibraryUiState>
    fun open(media: SavedMedia)
    fun removeFavorite(media: SavedMedia)
    fun removeWatchLater(media: SavedMedia)
    fun onSearchQueryChanged(query: String)
    fun onFilterSelected(filter: SavedMediaFilter)
    fun onSortSelected(sort: SavedMediaSort)
}

class DefaultLibraryComponent(
    componentContext: ComponentContext,
    private val repository: SavedMediaRepository,
    private val viewPreferences: LibraryViewPreferencesRepository,
    private val onMediaSelected: (SavedMedia) -> Unit,
    coroutineContext: kotlin.coroutines.CoroutineContext = kotlinx.coroutines.Dispatchers.Default
) : LibraryComponent, ComponentContext by componentContext {
    private data class Inputs(
        val favorites: List<SavedMedia> = emptyList(),
        val watchLater: List<SavedMedia> = emptyList(),
        val query: String = "",
        val filter: SavedMediaFilter = SavedMediaFilter.All,
        val sort: SavedMediaSort = SavedMediaSort.RecentlySaved,
        val loaded: Boolean = false,
    )

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + coroutineContext)
    private val mutableState = kotlinx.coroutines.flow.MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    private val inputs = kotlinx.coroutines.flow.MutableStateFlow(Inputs())
    override val state: StateFlow<LibraryUiState> = mutableState

    init {
        lifecycle.subscribe(object : com.arkivanov.essenty.lifecycle.Lifecycle.Callbacks {
            override fun onDestroy() { scope.cancel() }
        })
        scope.launch {
            combine(repository.observeFavorites(), repository.observeWatchLater(), viewPreferences.preferences) { favorites, watchLater, preferences ->
                Triple(favorites, watchLater, preferences)
            }
                .catch { mutableState.value = LibraryUiState.Error }
                .collect { (favorites, watchLater, preferences) ->
                    inputs.update {
                        it.copy(
                            favorites = favorites,
                            watchLater = watchLater,
                            filter = preferences.filter,
                            sort = preferences.sort,
                            loaded = true,
                        )
                    }
                }
        }
        scope.launch {
            inputs.filter { it.loaded }.collectLatest { snapshot ->
                val content = deriveContent(snapshot)
                if (inputs.value === snapshot) mutableState.value = content
            }
        }
    }

    override fun open(media: SavedMedia) = onMediaSelected(media)
    override fun removeFavorite(media: SavedMedia) { scope.launch { repository.setFavorite(media.toCatalogItem(), false) } }
    override fun removeWatchLater(media: SavedMedia) { scope.launch { repository.setWatchLater(media.toCatalogItem(), false) } }
    override fun onSearchQueryChanged(query: String) {
        inputs.update { it.copy(query = query) }
        mutableState.update { current ->
            if (current is LibraryUiState.Content) current.copy(searchQuery = query) else current
        }
    }
    override fun onFilterSelected(filter: SavedMediaFilter) {
        inputs.update { it.copy(filter = filter) }
        scope.launch {
            runCatching { viewPreferences.setFilter(filter) }
                .onFailure {
                    inputs.update { it.copy(filter = viewPreferences.preferences.value.filter) }
                }
        }
    }
    override fun onSortSelected(sort: SavedMediaSort) {
        inputs.update { it.copy(sort = sort) }
        scope.launch {
            runCatching { viewPreferences.setSort(sort) }
                .onFailure {
                    inputs.update { it.copy(sort = viewPreferences.preferences.value.sort) }
                }
        }
    }

    private fun deriveContent(snapshot: Inputs): LibraryUiState.Content {
        val normalizedQuery = snapshot.query.trim().lowercase()
        fun matches(item: SavedMedia) = normalizedQuery.isEmpty() || item.title.lowercase().contains(normalizedQuery) || item.authorTitle?.lowercase()?.contains(normalizedQuery) == true
        fun matchesFilter(item: SavedMedia) = when (snapshot.filter) {
            SavedMediaFilter.All -> item.isFavorite || item.isWatchLater
            SavedMediaFilter.Favorites -> item.isFavorite
            SavedMediaFilter.WatchLater -> item.isWatchLater
            SavedMediaFilter.Both -> item.isFavorite && item.isWatchLater
        }
        fun sortItems(items: List<SavedMedia>) = items.filter(::matches).filter(::matchesFilter).sortedWith(
            when (snapshot.sort) {
                SavedMediaSort.RecentlySaved -> compareByDescending<SavedMedia> { maxOf(it.favoriteAddedAtEpochMs ?: Long.MIN_VALUE, it.watchLaterAddedAtEpochMs ?: Long.MIN_VALUE) }
                SavedMediaSort.TitleAscending -> compareBy<SavedMedia> { it.title.lowercase() }
                SavedMediaSort.TitleDescending -> compareByDescending<SavedMedia> { it.title.lowercase() }
            }.thenBy { it.title.lowercase() }.thenBy { it.reference.provider.value }.thenBy { it.reference.externalId }
        )
        val both = snapshot.filter == SavedMediaFilter.Both
        return LibraryUiState.Content(
            favorites = sortItems(snapshot.favorites), watchLater = if (both) emptyList() else sortItems(snapshot.watchLater),
            searchQuery = snapshot.query, filter = snapshot.filter, sort = snapshot.sort,
            hasAnySavedMedia = snapshot.favorites.isNotEmpty() || snapshot.watchLater.isNotEmpty(),
            showFavorites = snapshot.filter != SavedMediaFilter.WatchLater,
            showWatchLater = snapshot.filter == SavedMediaFilter.All || snapshot.filter == SavedMediaFilter.WatchLater
        )
    }
}
