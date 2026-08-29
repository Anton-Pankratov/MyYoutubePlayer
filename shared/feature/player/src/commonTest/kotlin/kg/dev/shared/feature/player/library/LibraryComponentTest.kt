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

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryComponentTest {
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
        assertEquals(listOf(youtube), content.favorites); assertEquals(listOf(youtube, direct), content.watchLater)
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

    private fun media(provider: String, id: String, favorite: Boolean, later: Boolean) = SavedMedia(
        MediaReference(MediaProviderId(provider), id), provider, null, null, null, favorite, later, 1, 2
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
