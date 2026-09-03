package kg.dev.shared.feature.player.library

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import kg.dev.shared.core.common.media.MediaCatalogItem
import kg.dev.shared.core.common.media.MediaProviderId
import kg.dev.shared.core.common.media.MediaReference
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionComponentsTest {
    @Test fun listSupportsCreateRenameDeleteAndExternalUpdates() = runTest {
        val repository = FakeCollections()
        val opened = mutableListOf<CollectionId>()
        val lifecycle = LifecycleRegistry().also { it.onCreate() }
        val component = DefaultCollectionListComponent(
            DefaultComponentContext(lifecycle), repository, opened::add, StandardTestDispatcher(testScheduler)
        )
        advanceUntilIdle()
        assertIs<CollectionListUiState.Empty>(component.state.value)

        component.create("  First  ")
        advanceUntilIdle()
        val first = repository.collections().value.single()
        assertEquals("First", first.name)
        component.rename(first.id, " Renamed ")
        advanceUntilIdle()
        assertEquals("Renamed", repository.collections().value.single().name)
        assertEquals(listOf(first.id), opened)
        component.open(first.id)
        assertEquals(listOf(first.id, first.id), opened)

        repository.emitCollection(second("External", 2))
        advanceUntilIdle()
        assertEquals(2, assertIs<CollectionListUiState.Content>(component.state.value).collections.size)
        component.delete(first.id)
        advanceUntilIdle()
        assertEquals(listOf("External"), repository.collections().value.map { it.name })
        lifecycle.onDestroy()
    }

    @Test fun detailMapsCanonicalMediaAndReturnsNotFoundAfterExternalDeletion() = runTest {
        val repository = FakeCollections()
        val id = CollectionId("detail")
        val media = item("youtube", "same-id", "Title", "thumb", "Author", 7)
        repository.put(id, "Detail", listOf(media))
        val selected = mutableListOf<MediaCatalogItem>()
        var deleted = 0
        val lifecycle = LifecycleRegistry().also { it.onCreate() }
        val component = DefaultCollectionDetailComponent(
            DefaultComponentContext(lifecycle), id, repository, selected::add, { deleted++ }, StandardTestDispatcher(testScheduler)
        )
        advanceUntilIdle()
        val content = assertIs<CollectionDetailUiState.Content>(component.state.value)
        component.open(content.detail.items.single())
        assertEquals(listOf(media), selected)
        component.remove(content.detail.items.single())
        advanceUntilIdle()
        assertTrue(assertIs<CollectionDetailUiState.Content>(component.state.value).detail.items.isEmpty())
        repository.delete(id)
        advanceUntilIdle()
        assertIs<CollectionDetailUiState.NotFound>(component.state.value)
        assertEquals(1, deleted)
        lifecycle.onDestroy()
    }

    @Test fun pickerUsesDesiredMembershipAndCancelDoesNotWrite() = runTest {
        val repository = FakeCollections()
        val a = CollectionId("a")
        val b = CollectionId("b")
        val media = item("direct", "same", "Item")
        repository.put(a, "A", listOf(media))
        repository.put(b, "B")
        val picker = CollectionPickerComponent(repository, media, StandardTestDispatcher(testScheduler))
        advanceUntilIdle()
        assertEquals(setOf(a), picker.state.value.selected)
        picker.toggle(a)
        picker.toggle(b)
        picker.confirm { }
        advanceUntilIdle()
        assertEquals(setOf(b), repository.collectionIdsContaining(media.reference).value)
        val writes = repository.addCalls + repository.removeCalls
        val cancelled = CollectionPickerComponent(repository, media, StandardTestDispatcher(testScheduler))
        advanceUntilIdle()
        cancelled.cancel()
        assertEquals(writes, repository.addCalls + repository.removeCalls)
    }

    @Test fun hubNavigatesSavedCollectionsDetailAndReturnsAfterExternalDelete() = runTest {
        val collections = FakeCollections()
        val id = CollectionId("hub")
        collections.put(id, "Hub")
        val lifecycle = LifecycleRegistry().also { it.onCreate() }
        val hub = DefaultLibraryHubComponent(
            DefaultComponentContext(lifecycle), FakeSavedMedia(), FakePreferences(), collections, {},
            StandardTestDispatcher(testScheduler)
        )
        hub.showCollections()
        assertIs<LibraryHubDestination.Collections>(hub.destination.value)
        hub.collections.open(id)
        assertEquals(LibraryHubDestination.Detail(id), hub.destination.value)
        hub.detail(id)
        advanceUntilIdle()
        collections.delete(id)
        advanceUntilIdle()
        assertIs<LibraryHubDestination.Collections>(hub.destination.value)
        hub.showSaved()
        assertIs<LibraryHubDestination.Saved>(hub.destination.value)
        lifecycle.onDestroy()
    }

    @Test fun renameDialogRejectsBlankDraftWithoutRepositoryWrite() = runTest {
        val repository = FakeCollections(); val collection = second("Old", 1); repository.emitCollection(collection)
        val component = listComponent(repository, StandardTestDispatcher(testScheduler))
        component.requestRename(collection)
        component.updateRenameDraft("   ")
        component.confirmRename()
        advanceUntilIdle()
        assertIs<CollectionDialogState.Rename>(component.dialog.value)
        assertEquals(0, repository.renameCalls)
        assertEquals("Old", repository.collections().value.single().name)
        assertEquals(0, repository.deleteCalls + repository.addCalls + repository.removeCalls)
    }

    @Test fun renameDialogCancelDoesNotWriteAndCloses() = runTest {
        val repository = FakeCollections(); val collection = second("Old", 1); repository.emitCollection(collection)
        val component = listComponent(repository, StandardTestDispatcher(testScheduler))
        component.requestRename(collection); component.updateRenameDraft("New"); component.cancelDialog()
        assertIs<CollectionDialogState.None>(component.dialog.value)
        assertEquals(0, repository.renameCalls + repository.deleteCalls + repository.addCalls + repository.removeCalls)
        assertEquals("Old", repository.collections().value.single().name)
    }

    @Test fun renameDialogConfirmTrimsViaRepositoryAndCloses() = runTest {
        val repository = FakeCollections(); val collection = second("Old", 1); repository.emitCollection(collection)
        val component = listComponent(repository, StandardTestDispatcher(testScheduler))
        component.requestRename(collection); component.updateRenameDraft("  New Name  "); component.confirmRename(); advanceUntilIdle()
        assertIs<CollectionDialogState.None>(component.dialog.value)
        assertEquals(1, repository.renameCalls)
        assertEquals("New Name", repository.collections().value.single().name)
    }

    @Test fun deleteDialogRequiresConfirmationAndCancelIsZeroWrite() = runTest {
        val repository = FakeCollections(); val collection = second("Delete", 1); repository.emitCollection(collection)
        val component = listComponent(repository, StandardTestDispatcher(testScheduler))
        component.requestDelete(collection)
        assertIs<CollectionDialogState.Delete>(component.dialog.value)
        assertEquals(0, repository.deleteCalls)
        component.cancelDialog()
        assertIs<CollectionDialogState.None>(component.dialog.value)
        assertEquals(0, repository.deleteCalls + repository.renameCalls + repository.addCalls + repository.removeCalls)
        assertEquals(1, repository.collections().value.size)
    }

    @Test fun deleteDialogConfirmDeletesOnceAndClosesReactively() = runTest {
        val repository = FakeCollections(); val collection = second("Delete", 1); repository.emitCollection(collection)
        val component = listComponent(repository, StandardTestDispatcher(testScheduler))
        component.requestDelete(collection); component.confirmDelete(); advanceUntilIdle()
        assertIs<CollectionDialogState.None>(component.dialog.value)
        assertEquals(1, repository.deleteCalls)
        assertEquals(listOf(collection.id), repository.deletedIds)
        assertTrue(repository.collections().value.isEmpty())
    }

    @Test fun detailMoveActionsMapToProviderQualifiedBeforeReferences() = runTest {
        val repository = FakeCollections(); val id = CollectionId("move")
        val a = item("youtube", "a", "A"); val b = item("direct", "same", "B"); val c = item("youtube", "same", "C")
        repository.put(id, "Move", listOf(a, b, c))
        val lifecycle = LifecycleRegistry().also { it.onCreate() }
        val detail = DefaultCollectionDetailComponent(DefaultComponentContext(lifecycle), id, repository, {}, {}, StandardTestDispatcher(testScheduler))
        advanceUntilIdle()
        detail.moveUp(c.reference); advanceUntilIdle()
        assertEquals(c.reference to b.reference, repository.moveCalls.single())
        detail.moveDown(c.reference); advanceUntilIdle()
        assertEquals(c.reference to null, repository.moveCalls.last())
        val writes = repository.moveCalls.size
        detail.moveUp(a.reference); detail.moveDown(c.reference); advanceUntilIdle()
        assertEquals(writes, repository.moveCalls.size)
    }

    @Test fun detailMoveBeforeForwardsOneProviderQualifiedSemanticMove() = runTest {
        val repository = FakeCollections(); val id = CollectionId("drag")
        val youtube = item("youtube", "same", "YouTube")
        val direct = item("direct", "same", "Direct")
        repository.put(id, "Drag", listOf(youtube, direct))
        val lifecycle = LifecycleRegistry().also { it.onCreate() }
        val detail = DefaultCollectionDetailComponent(DefaultComponentContext(lifecycle), id, repository, {}, {}, StandardTestDispatcher(testScheduler))
        advanceUntilIdle()

        detail.moveBefore(direct.reference, youtube.reference)
        advanceUntilIdle()

        assertEquals(
            listOf(Pair<MediaReference, MediaReference?>(direct.reference, youtube.reference)),
            repository.moveCalls,
        )
        assertEquals(listOf(direct.reference, youtube.reference),
            assertIs<CollectionDetailUiState.Content>(detail.state.value).detail.items.map { it.reference })
        lifecycle.onDestroy()
    }

    private fun listComponent(repository: FakeCollections, dispatcher: TestDispatcher): DefaultCollectionListComponent {
        val lifecycle = LifecycleRegistry().also { it.onCreate() }
        return DefaultCollectionListComponent(DefaultComponentContext(lifecycle), repository, {}, dispatcher)
    }

    private fun item(provider: String, id: String, title: String, thumbnail: String? = null, author: String? = null, duration: Long? = null) =
        MediaCatalogItem(MediaReference(MediaProviderId(provider), id), title, thumbnail, author, duration)

    private fun second(name: String, time: Long) = MediaCollection(CollectionId(name.lowercase()), name, time, time, 0)
}

private class FakeCollections : MediaCollectionRepository {
    private val collectionsState = MutableStateFlow<List<MediaCollection>>(emptyList())
    private val details = mutableMapOf<CollectionId, MutableStateFlow<MediaCollectionDetail?>>()
    private val memberships = mutableMapOf<MediaReference, MutableStateFlow<Set<CollectionId>>>()
    var addCalls = 0
    var removeCalls = 0
    var renameCalls = 0
    var deleteCalls = 0
    val moveCalls = mutableListOf<Pair<MediaReference, MediaReference?>>()
    val deletedIds = mutableListOf<CollectionId>()

    override fun collections(): StateFlow<List<MediaCollection>> = collectionsState
    override fun observeCollection(id: CollectionId): StateFlow<MediaCollectionDetail?> = details.getOrPut(id) { MutableStateFlow(null) }
    override fun collectionIdsContaining(reference: MediaReference): StateFlow<Set<CollectionId>> = memberships.getOrPut(reference) {
        MutableStateFlow(containing(reference))
    }
    override suspend fun create(name: String): CollectionId {
        require(name.trim().isNotEmpty())
        val id = CollectionId("created-${collectionsState.value.size}")
        put(id, name.trim())
        return id
    }
    override suspend fun rename(id: CollectionId, name: String) {
        require(name.trim().isNotEmpty())
        renameCalls++
        val detail = requireNotNull(observeCollection(id).value)
        put(id, name.trim(), detail.items.map { it.toCatalogItem() })
    }
    override suspend fun delete(id: CollectionId) {
        val detail = observeCollection(id).value ?: return
        deleteCalls++
        deletedIds += id
        detail.items.forEach { memberships[it.reference]?.value = memberships[it.reference]?.value.orEmpty() - id }
        details.getOrPut(id) { MutableStateFlow(null) }.value = null
        collectionsState.value = collectionsState.value.filterNot { it.id == id }
    }
    override suspend fun addMedia(id: CollectionId, media: MediaCatalogItem) {
        val detail = requireNotNull(observeCollection(id).value)
        if (detail.items.any { it.reference == media.reference }) return
        addCalls++
        put(id, detail.collection.name, detail.items.map { it.toCatalogItem() } + media)
    }
    override suspend fun removeMedia(id: CollectionId, reference: MediaReference) {
        val detail = requireNotNull(observeCollection(id).value)
        if (detail.items.none { it.reference == reference }) return
        removeCalls++
        put(id, detail.collection.name, detail.items.filterNot { it.reference == reference }.map { it.toCatalogItem() })
    }
    override suspend fun moveMedia(id: CollectionId, reference: MediaReference, before: MediaReference?) {
        val detail = requireNotNull(observeCollection(id).value)
        val source = detail.items.first { it.reference == reference }.toCatalogItem()
        moveCalls += reference to before
        val reordered = detail.items.filterNot { it.reference == reference }.map { it.toCatalogItem() }.toMutableList()
        reordered.add(before?.let { target -> reordered.indexOfFirst { it.reference == target } }.takeIf { it != null && it >= 0 } ?: reordered.size, source)
        put(id, detail.collection.name, reordered)
    }
    fun put(id: CollectionId, name: String, media: List<MediaCatalogItem> = emptyList()) {
        val previous = details[id]?.value?.collection
        val collection = MediaCollection(id, name, previous?.createdAtEpochMs ?: 1, previous?.updatedAtEpochMs ?: 1, media.size.toLong())
        val detail = MediaCollectionDetail(collection, media.map { CollectionMedia(id, it.reference, it.title, it.thumbnailUrl, it.authorTitle, it.durationMs, 1) })
        details.getOrPut(id) { MutableStateFlow(null) }.value = detail
        collectionsState.value = (collectionsState.value.filterNot { it.id == id } + collection).sortedBy { it.name }
        refreshMemberships()
    }
    fun emitCollection(collection: MediaCollection) = put(collection.id, collection.name)
    private fun refreshMemberships() = memberships.forEach { (reference, flow) -> flow.value = containing(reference) }
    private fun containing(reference: MediaReference) = details.values.mapNotNull { it.value }
        .filter { detail -> detail.items.any { it.reference == reference } }
        .map { it.collection.id }
        .toSet()
}

private class FakeSavedMedia : SavedMediaRepository {
    private val favoritesState = MutableStateFlow<List<SavedMedia>>(emptyList())
    private val watchLaterState = MutableStateFlow<List<SavedMedia>>(emptyList())
    private val states = mutableMapOf<MediaReference, MutableStateFlow<SavedMediaState>>()
    override fun observe(reference: MediaReference): StateFlow<SavedMediaState> = states.getOrPut(reference) { MutableStateFlow(SavedMediaState()) }
    override fun favorites(): StateFlow<List<SavedMedia>> = favoritesState
    override fun watchLater(): StateFlow<List<SavedMedia>> = watchLaterState
    override suspend fun setFavorite(item: MediaCatalogItem, enabled: Boolean) = Unit
    override suspend fun setWatchLater(item: MediaCatalogItem, enabled: Boolean) = Unit
}

private class FakePreferences : LibraryViewPreferencesRepository {
    override val preferences = MutableStateFlow(LibraryViewPreferences())
    override suspend fun setFilter(filter: SavedMediaFilter) { preferences.value = preferences.value.copy(filter = filter) }
    override suspend fun setSort(sort: SavedMediaSort) { preferences.value = preferences.value.copy(sort = sort) }
}
