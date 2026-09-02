package kg.dev.shared.feature.player.library

import com.arkivanov.decompose.ComponentContext
import kg.dev.shared.core.common.media.MediaCatalogItem
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface CollectionListUiState {
    data object Loading : CollectionListUiState
    data object Empty : CollectionListUiState
    data class Content(val collections: List<MediaCollection>) : CollectionListUiState
    data object Error : CollectionListUiState
}

sealed interface CollectionDialogState {
    data object None : CollectionDialogState
    data class Rename(val collection: MediaCollection, val draft: String) : CollectionDialogState
    data class Delete(val collection: MediaCollection) : CollectionDialogState
}

interface CollectionListComponent {
    val state: StateFlow<CollectionListUiState>
    val dialog: StateFlow<CollectionDialogState>
    fun create(name: String)
    fun rename(id: CollectionId, name: String)
    fun delete(id: CollectionId)
    fun requestRename(collection: MediaCollection)
    fun updateRenameDraft(name: String)
    fun confirmRename()
    fun cancelDialog()
    fun requestDelete(collection: MediaCollection)
    fun confirmDelete()
    fun open(id: CollectionId)
}

class DefaultCollectionListComponent(
    componentContext: ComponentContext,
    private val repository: MediaCollectionRepository,
    private val onOpen: (CollectionId) -> Unit,
    coroutineContext: kotlin.coroutines.CoroutineContext = kotlinx.coroutines.Dispatchers.Default
) : CollectionListComponent, ComponentContext by componentContext {
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + coroutineContext)
    private val mutableState = MutableStateFlow<CollectionListUiState>(CollectionListUiState.Loading)
    override val state: StateFlow<CollectionListUiState> = mutableState
    private val mutableDialog = MutableStateFlow<CollectionDialogState>(CollectionDialogState.None)
    override val dialog: StateFlow<CollectionDialogState> = mutableDialog
    init {
        lifecycle.subscribe(object : com.arkivanov.essenty.lifecycle.Lifecycle.Callbacks { override fun onDestroy() = scope.cancel() })
        scope.launch { repository.collections().collect { collections -> mutableState.value = if (collections.isEmpty()) CollectionListUiState.Empty else CollectionListUiState.Content(collections) } }
    }
    override fun create(name: String) { scope.launch { runCatching { repository.create(name) }.onSuccess(onOpen).onFailure { mutableState.value = CollectionListUiState.Error } } }
    override fun rename(id: CollectionId, name: String) { scope.launch { runCatching { repository.rename(id, name) }.onFailure { mutableState.value = CollectionListUiState.Error } } }
    override fun delete(id: CollectionId) { scope.launch { runCatching { repository.delete(id) }.onFailure { mutableState.value = CollectionListUiState.Error } } }
    override fun requestRename(collection: MediaCollection) { mutableDialog.value = CollectionDialogState.Rename(collection, collection.name) }
    override fun updateRenameDraft(name: String) {
        val current = mutableDialog.value as? CollectionDialogState.Rename ?: return
        mutableDialog.value = current.copy(draft = name)
    }
    override fun confirmRename() {
        val current = mutableDialog.value as? CollectionDialogState.Rename ?: return
        scope.launch {
            runCatching { repository.rename(current.collection.id, current.draft) }
                .onSuccess { mutableDialog.value = CollectionDialogState.None }
                .onFailure { mutableState.value = CollectionListUiState.Error }
        }
    }
    override fun cancelDialog() { mutableDialog.value = CollectionDialogState.None }
    override fun requestDelete(collection: MediaCollection) { mutableDialog.value = CollectionDialogState.Delete(collection) }
    override fun confirmDelete() {
        val current = mutableDialog.value as? CollectionDialogState.Delete ?: return
        scope.launch {
            runCatching { repository.delete(current.collection.id) }
                .onSuccess { mutableDialog.value = CollectionDialogState.None }
                .onFailure { mutableState.value = CollectionListUiState.Error }
        }
    }
    override fun open(id: CollectionId) = onOpen(id)
}

sealed interface CollectionDetailUiState {
    data object Loading : CollectionDetailUiState
    data object NotFound : CollectionDetailUiState
    data class Content(val detail: MediaCollectionDetail) : CollectionDetailUiState
    data object Error : CollectionDetailUiState
}

interface CollectionDetailComponent {
    val state: StateFlow<CollectionDetailUiState>
    fun remove(media: CollectionMedia)
    fun open(media: CollectionMedia)
}

class DefaultCollectionDetailComponent(
    componentContext: ComponentContext,
    private val id: CollectionId,
    private val repository: MediaCollectionRepository,
    private val onMediaSelected: (MediaCatalogItem) -> Unit,
    private val onDeleted: () -> Unit,
    coroutineContext: kotlin.coroutines.CoroutineContext = kotlinx.coroutines.Dispatchers.Default
) : CollectionDetailComponent, ComponentContext by componentContext {
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + coroutineContext)
    private val mutableState = MutableStateFlow<CollectionDetailUiState>(CollectionDetailUiState.Loading)
    override val state: StateFlow<CollectionDetailUiState> = mutableState
    init {
        lifecycle.subscribe(object : com.arkivanov.essenty.lifecycle.Lifecycle.Callbacks { override fun onDestroy() = scope.cancel() })
        scope.launch { repository.observeCollection(id).collect { detail ->
            if (detail == null) { mutableState.value = CollectionDetailUiState.NotFound; onDeleted() }
            else mutableState.value = CollectionDetailUiState.Content(detail)
        } }
    }
    override fun remove(media: CollectionMedia) { scope.launch { runCatching { repository.removeMedia(id, media.reference) }.onFailure { mutableState.value = CollectionDetailUiState.Error } } }
    override fun open(media: CollectionMedia) = onMediaSelected(media.toCatalogItem())
}
