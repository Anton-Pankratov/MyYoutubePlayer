package kg.dev.shared.feature.player.library

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import kg.dev.shared.core.common.media.MediaCatalogItem
import kg.dev.shared.core.common.media.MediaProviderId
import kg.dev.shared.core.common.media.MediaReference
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryComponentTest {
    @Test fun matchingFiltersSortingAndRepositoryUpdatesRemainPresentationOnly() = runTest {
        val lifecycle = LifecycleRegistry().also { it.onCreate() }
        val repo = FakeSavedMediaRepository()
        val component = DefaultLibraryComponent(DefaultComponentContext(lifecycle), repo, {}, StandardTestDispatcher(testScheduler))
        val f = media("youtube", "same", true, false, "Same", "Lofi Author", 10, null)
        val w = media("direct", "same", false, true, "Same", "Other", null, 20)
        val b = media("test", "both", true, true, "Beta", "Author", 5, 30)
        repo.favoritesState.value = listOf(f, b); repo.watchState.value = listOf(w, b); advanceUntilIdle()

        component.onSearchQueryChanged("   ")
        var content = assertIs<LibraryUiState.Content>(component.state.value)
        assertEquals("", content.searchQuery.trim()); assertEquals(SavedMediaFilter.All, content.filter); assertEquals(SavedMediaSort.RecentlySaved, content.sort)
        assertEquals(listOf("Beta", "Same"), content.favorites.map { it.title })
        component.onSearchQueryChanged("  lOfI "); content = assertIs(component.state.value)
        assertEquals(listOf(f), content.favorites); assertTrue(content.watchLater.isEmpty())
        component.onSearchQueryChanged("missing"); content = assertIs(component.state.value)
        assertTrue(content.hasAnySavedMedia); assertTrue(content.favorites.isEmpty() && content.watchLater.isEmpty())

        component.onSearchQueryChanged("same"); content = assertIs(component.state.value)
        assertEquals(listOf(f.reference), content.favorites.map { it.reference })
        assertEquals(listOf(w.reference), content.watchLater.map { it.reference })

        component.onSearchQueryChanged(""); component.onFilterSelected(SavedMediaFilter.All); content = assertIs(component.state.value)
        assertEquals(listOf("Beta", "Same"), content.favorites.map { it.title }); assertEquals(listOf("Beta", "Same"), content.watchLater.map { it.title })
        component.onFilterSelected(SavedMediaFilter.WatchLater); content = assertIs(component.state.value)
        assertEquals(listOf("Beta", "Same"), content.watchLater.map { it.title }); assertTrue(!content.showFavorites && content.showWatchLater)
        component.onFilterSelected(SavedMediaFilter.Both); content = assertIs(component.state.value)
        assertEquals(listOf(b), content.favorites); assertTrue(!content.showWatchLater)
        component.onFilterSelected(SavedMediaFilter.Favorites); component.onSortSelected(SavedMediaSort.TitleAscending); content = assertIs(component.state.value)
        assertEquals(listOf("Beta", "Same"), content.favorites.map { it.title })
        component.onSortSelected(SavedMediaSort.TitleDescending); content = assertIs(component.state.value)
        assertEquals(listOf("Same", "Beta"), content.favorites.map { it.title })

        component.onFilterSelected(SavedMediaFilter.Both); component.removeFavorite(b); advanceUntilIdle()
        assertEquals(listOf(false), repo.favoriteWrites.map { it.second })
        repo.favoritesState.value = listOf(f); repo.watchState.value = listOf(w); advanceUntilIdle()
        content = assertIs(component.state.value)
        assertEquals("", content.searchQuery); assertEquals(SavedMediaFilter.Both, content.filter); assertEquals(SavedMediaSort.TitleDescending, content.sort)
        assertTrue(content.favorites.isEmpty())
        repo.favoritesState.value = listOf(f, b); repo.watchState.value = listOf(w, b); advanceUntilIdle()
        assertEquals(listOf(b), assertIs<LibraryUiState.Content>(component.state.value).favorites)
        assertEquals(1, repo.favoriteWrites.size); assertEquals(0, repo.watchWrites.size)
        lifecycle.onDestroy()
    }
    @Test fun searchFilterAndSortAreDerivedWithoutPersistenceWrites() = runTest {
        val lifecycle = LifecycleRegistry().also { it.onCreate() }
        val repo = FakeSavedMediaRepository()
        val component = DefaultLibraryComponent(DefaultComponentContext(lifecycle), repo, {}, StandardTestDispatcher(testScheduler))
        val favorite = media("youtube", "same", true, false, "Lofi Morning", "Artist", 10, null)
        val watchLater = media("direct", "same", false, true, "Lofi Night", "Artist", null, 20)
        val both = media("test", "both", true, true, "Ambient Lofi", "Author", 15, 30)
        repo.favoritesState.value = listOf(favorite, both); repo.watchState.value = listOf(watchLater, both); advanceUntilIdle()
        component.onSearchQueryChanged("  LOFI "); component.onFilterSelected(SavedMediaFilter.Favorites); component.onSortSelected(SavedMediaSort.TitleAscending)
        val content = assertIs<LibraryUiState.Content>(component.state.value)
        assertEquals(listOf("Ambient Lofi", "Lofi Morning"), content.favorites.map { it.title })
        assertEquals(listOf("Ambient Lofi"), content.watchLater.map { it.title })
        assertEquals(0, repo.favoriteWrites.size); assertEquals(0, repo.watchWrites.size)
        component.onFilterSelected(SavedMediaFilter.Both); assertEquals(listOf("Ambient Lofi"), assertIs<LibraryUiState.Content>(component.state.value).favorites.map { it.title })
        lifecycle.onDestroy()
    }
    @Test fun loadingContentErrorsActionsAndIdentityAreProviderQualified() = runTest {
        val lifecycle = LifecycleRegistry().also { it.onCreate() }
        val repo = FakeSavedMediaRepository()
        val selected = mutableListOf<SavedMedia>()
        val component = DefaultLibraryComponent(DefaultComponentContext(lifecycle), repo, selected::add, StandardTestDispatcher(testScheduler))
        assertIs<LibraryUiState.Loading>(component.state.value)
        val youtube = media("youtube", "same", true, true)
        val direct = media("direct", "same", false, true)
        repo.favoritesState.value = listOf(youtube)
        repo.watchState.value = listOf(youtube, direct)
        advanceUntilIdle()
        val content = assertIs<LibraryUiState.Content>(component.state.value)
        assertEquals(listOf(youtube), content.favorites); assertEquals(listOf(direct, youtube), content.watchLater)
        component.removeFavorite(youtube); component.removeWatchLater(youtube); component.open(youtube); component.open(direct)
        advanceUntilIdle()
        assertEquals(listOf(false), repo.favoriteWrites.map { it.second }); assertEquals(listOf(false), repo.watchWrites.map { it.second })
        assertEquals(listOf(MediaProviderId("youtube"), MediaProviderId("direct")), selected.map { it.reference.provider })
        assertEquals(listOf("same", "same"), selected.map { it.reference.externalId })
        lifecycle.onDestroy()
    }

    @Test fun repositoryFailureProducesNormalizedError() = runTest {
        val lifecycle = LifecycleRegistry().also { it.onCreate() }
        val repo = FakeSavedMediaRepository(favoritesFlow = flow { throw IllegalStateException("db") })
        val component = DefaultLibraryComponent(DefaultComponentContext(lifecycle), repo, {}, StandardTestDispatcher(testScheduler))
        advanceUntilIdle()
        assertIs<LibraryUiState.Error>(component.state.value)
        lifecycle.onDestroy()
    }

    private fun media(provider: String, id: String, favorite: Boolean, later: Boolean, title: String = provider, author: String? = null, favoriteAt: Long? = 1, laterAt: Long? = 2) = SavedMedia(
        MediaReference(MediaProviderId(provider), id), title, null, author, null, favorite, later, favoriteAt, laterAt
    )
}

private class FakeSavedMediaRepository(
    private val favoritesFlow: Flow<List<SavedMedia>>? = null
) : SavedMediaRepository {
    val favoritesState = MutableStateFlow(emptyList<SavedMedia>())
    val watchState = MutableStateFlow(emptyList<SavedMedia>())
    val favoriteWrites = mutableListOf<Pair<MediaCatalogItem, Boolean>>()
    val watchWrites = mutableListOf<Pair<MediaCatalogItem, Boolean>>()
    private val states = mutableMapOf<MediaReference, MutableStateFlow<SavedMediaState>>()
    override fun observe(reference: MediaReference) = states.getOrPut(reference) { MutableStateFlow(SavedMediaState()) }
    override fun favorites() = favoritesState
    override fun watchLater() = watchState
    override fun observeFavorites() = favoritesFlow ?: favoritesState
    override fun observeWatchLater() = watchState
    override suspend fun setFavorite(item: MediaCatalogItem, enabled: Boolean) { favoriteWrites += item to enabled }
    override suspend fun setWatchLater(item: MediaCatalogItem, enabled: Boolean) { watchWrites += item to enabled }
}
