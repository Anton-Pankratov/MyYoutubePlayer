package kg.dev.shared.feature.player.library

import com.arkivanov.decompose.ComponentContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
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
    private val onMediaSelected: (SavedMedia) -> Unit,
    coroutineContext: kotlin.coroutines.CoroutineContext = kotlinx.coroutines.Dispatchers.Default
) : LibraryComponent, ComponentContext by componentContext {
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + coroutineContext)
    private val mutableState = kotlinx.coroutines.flow.MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    private var sourceFavorites = emptyList<SavedMedia>()
    private var sourceWatchLater = emptyList<SavedMedia>()
    private var query = ""
    private var filter = SavedMediaFilter.All
    private var sort = SavedMediaSort.RecentlySaved
    override val state: StateFlow<LibraryUiState> = mutableState

    init {
        lifecycle.subscribe(object : com.arkivanov.essenty.lifecycle.Lifecycle.Callbacks {
            override fun onDestroy() { scope.cancel() }
        })
        scope.launch {
            combine(repository.observeFavorites(), repository.observeWatchLater()) { favorites, watchLater -> favorites to watchLater }
                .catch { mutableState.value = LibraryUiState.Error }
                .collect { (favorites, watchLater) -> sourceFavorites = favorites; sourceWatchLater = watchLater; publishContent() }
        }
    }

    override fun open(media: SavedMedia) = onMediaSelected(media)
    override fun removeFavorite(media: SavedMedia) { scope.launch { repository.setFavorite(media.toCatalogItem(), false) } }
    override fun removeWatchLater(media: SavedMedia) { scope.launch { repository.setWatchLater(media.toCatalogItem(), false) } }
    override fun onSearchQueryChanged(query: String) { this.query = query; publishContent() }
    override fun onFilterSelected(filter: SavedMediaFilter) { this.filter = filter; publishContent() }
    override fun onSortSelected(sort: SavedMediaSort) { this.sort = sort; publishContent() }

    private fun publishContent() {
        val normalizedQuery = query.trim().lowercase()
        fun matches(item: SavedMedia) = normalizedQuery.isEmpty() || item.title.lowercase().contains(normalizedQuery) || item.authorTitle?.lowercase()?.contains(normalizedQuery) == true
        fun matchesFilter(item: SavedMedia) = when (filter) {
            SavedMediaFilter.All -> item.isFavorite || item.isWatchLater
            SavedMediaFilter.Favorites -> item.isFavorite
            SavedMediaFilter.WatchLater -> item.isWatchLater
            SavedMediaFilter.Both -> item.isFavorite && item.isWatchLater
        }
        fun sortItems(items: List<SavedMedia>) = items.filter(::matches).filter(::matchesFilter).sortedWith(
            when (sort) {
                SavedMediaSort.RecentlySaved -> compareByDescending<SavedMedia> { maxOf(it.favoriteAddedAtEpochMs ?: Long.MIN_VALUE, it.watchLaterAddedAtEpochMs ?: Long.MIN_VALUE) }
                SavedMediaSort.TitleAscending -> compareBy<SavedMedia> { it.title.lowercase() }
                SavedMediaSort.TitleDescending -> compareByDescending<SavedMedia> { it.title.lowercase() }
            }.thenBy { it.title.lowercase() }.thenBy { it.reference.provider.value }.thenBy { it.reference.externalId }
        )
        val both = filter == SavedMediaFilter.Both
        mutableState.value = LibraryUiState.Content(
            favorites = sortItems(sourceFavorites), watchLater = if (both) emptyList() else sortItems(sourceWatchLater),
            searchQuery = query, filter = filter, sort = sort,
            hasAnySavedMedia = sourceFavorites.isNotEmpty() || sourceWatchLater.isNotEmpty(),
            showFavorites = filter != SavedMediaFilter.WatchLater,
            showWatchLater = filter == SavedMediaFilter.All || filter == SavedMediaFilter.WatchLater
        )
    }
}
