package kg.dev.shared.feature.player.library

import kg.dev.shared.core.common.media.MediaCatalogItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

data class CollectionPickerUiState(val collections: List<MediaCollection> = emptyList(), val selected: Set<CollectionId> = emptySet())

class CollectionPickerComponent(
    private val repository: MediaCollectionRepository,
    private val media: MediaCatalogItem,
    coroutineContext: kotlin.coroutines.CoroutineContext = kotlinx.coroutines.Dispatchers.Default
) {
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + coroutineContext)
    private var initial = emptySet<CollectionId>()
    private val mutableState = MutableStateFlow(CollectionPickerUiState())
    val state: StateFlow<CollectionPickerUiState> = mutableState
    init {
        scope.launch {
            kotlinx.coroutines.flow.combine(repository.collections(), repository.collectionIdsContaining(media.reference)) { collections, memberships -> collections to memberships }
                .collect { (collections, memberships) ->
                    if (initial.isEmpty() && mutableState.value.collections.isEmpty()) initial = memberships
                    mutableState.value = CollectionPickerUiState(collections, mutableState.value.selected.ifEmpty { memberships })
                }
        }
    }
    fun toggle(id: CollectionId) { mutableState.value = mutableState.value.run { copy(selected = if (id in selected) selected - id else selected + id) } }
    fun confirm(onComplete: () -> Unit) { scope.launch { val desired = mutableState.value.selected; (desired - initial).forEach { repository.addMedia(it, media) }; (initial - desired).forEach { repository.removeMedia(it, media.reference) }; onComplete() } }
    fun cancel() { scope.cancel() }
}
