package kg.dev.shared.feature.player.library

import com.arkivanov.decompose.ComponentContext
import kg.dev.shared.core.common.media.MediaCatalogItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed interface LibraryHubDestination {
    data object Saved : LibraryHubDestination
    data object Collections : LibraryHubDestination
    data class Detail(val id: CollectionId) : LibraryHubDestination
}

interface LibraryHubComponent {
    val destination: StateFlow<LibraryHubDestination>
    val savedLibrary: LibraryComponent
    val collections: CollectionListComponent
    fun picker(media: SavedMedia): CollectionPickerComponent
    fun detail(id: CollectionId): CollectionDetailComponent
    fun showSaved()
    fun showCollections()
}

class DefaultLibraryHubComponent(
    private val componentContext: ComponentContext,
    savedMediaRepository: SavedMediaRepository,
    viewPreferences: LibraryViewPreferencesRepository,
    private val collectionRepository: MediaCollectionRepository,
    private val onMediaSelected: (MediaCatalogItem) -> Unit,
    private val coroutineContext: kotlin.coroutines.CoroutineContext = kotlinx.coroutines.Dispatchers.Default
) : LibraryHubComponent, ComponentContext by componentContext {
    private val mutableDestination = MutableStateFlow<LibraryHubDestination>(LibraryHubDestination.Saved)
    override val destination: StateFlow<LibraryHubDestination> = mutableDestination
    override val savedLibrary = DefaultLibraryComponent(componentContext, savedMediaRepository, viewPreferences, { onMediaSelected(it.toCatalogItem()) }, coroutineContext)
    override val collections = DefaultCollectionListComponent(componentContext, collectionRepository, ::openDetail, coroutineContext)
    private val details = mutableMapOf<CollectionId, CollectionDetailComponent>()

    override fun detail(id: CollectionId): CollectionDetailComponent = details.getOrPut(id) {
        DefaultCollectionDetailComponent(componentContext, id, collectionRepository, onMediaSelected, ::showCollections, coroutineContext)
    }
    override fun showSaved() { mutableDestination.value = LibraryHubDestination.Saved }
    override fun showCollections() { mutableDestination.value = LibraryHubDestination.Collections }
    override fun picker(media: SavedMedia) = CollectionPickerComponent(collectionRepository, media.toCatalogItem(), coroutineContext)
    private fun openDetail(id: CollectionId) { mutableDestination.value = LibraryHubDestination.Detail(id) }
}
