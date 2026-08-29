package kg.dev.shared.feature.player.library

import com.arkivanov.decompose.ComponentContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data class Content(val favorites: List<SavedMedia>, val watchLater: List<SavedMedia>) : LibraryUiState
    data object Error : LibraryUiState
}

interface LibraryComponent {
    val state: StateFlow<LibraryUiState>
    fun open(media: SavedMedia)
    fun removeFavorite(media: SavedMedia)
    fun removeWatchLater(media: SavedMedia)
}

class DefaultLibraryComponent(
    componentContext: ComponentContext,
    private val repository: SavedMediaRepository,
    private val onMediaSelected: (SavedMedia) -> Unit,
    coroutineContext: kotlin.coroutines.CoroutineContext = kotlinx.coroutines.Dispatchers.Default
) : LibraryComponent, ComponentContext by componentContext {
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + coroutineContext)
    private val mutableState = kotlinx.coroutines.flow.MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    override val state: StateFlow<LibraryUiState> = mutableState

    init {
        lifecycle.subscribe(object : com.arkivanov.essenty.lifecycle.Lifecycle.Callbacks {
            override fun onDestroy() { scope.cancel() }
        })
        scope.launch {
            combine(repository.observeFavorites(), repository.observeWatchLater()) { favorites, watchLater ->
                LibraryUiState.Content(favorites, watchLater)
            }.catch { mutableState.value = LibraryUiState.Error }
                .collect { mutableState.value = it }
        }
    }

    override fun open(media: SavedMedia) = onMediaSelected(media)
    override fun removeFavorite(media: SavedMedia) { scope.launch { repository.setFavorite(media.toCatalogItem(), false) } }
    override fun removeWatchLater(media: SavedMedia) { scope.launch { repository.setWatchLater(media.toCatalogItem(), false) } }
}
