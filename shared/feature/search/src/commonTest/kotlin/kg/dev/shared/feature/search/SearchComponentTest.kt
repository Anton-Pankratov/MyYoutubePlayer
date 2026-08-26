package kg.dev.shared.feature.search

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import kg.dev.shared.core.common.Page
import kg.dev.shared.core.common.media.MediaCatalogItem
import kg.dev.shared.core.common.media.MediaProviders
import kg.dev.shared.core.common.media.MediaReference
import kg.dev.shared.feature.search.domain.model.Channel
import kg.dev.shared.feature.search.domain.model.SearchResult
import kg.dev.shared.feature.search.domain.repository.SearchRepository
import kg.dev.shared.feature.search.domain.usecase.SearchChannelsUseCase
import kg.dev.shared.feature.search.presentation.DefaultSearchComponent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SearchComponentTest {
    @Test
    fun videoSelectionIsEmittedToRootOwnedMediaCallback() = runTest {
        val lifecycle = LifecycleRegistry().also { it.onCreate() }
        var selected: MediaCatalogItem? = null
        val component = DefaultSearchComponent(
            DefaultComponentContext(lifecycle),
            SearchChannelsUseCase(RecordingRepository()),
            StandardTestDispatcher(testScheduler),
            debounceMillis = 0,
            onMediaSelected = { selected = it }
        )
        val video = MediaCatalogItem(
            MediaReference(MediaProviders.YouTube, "video-id"),
            "Video"
        )

        component.selectVideo(video)

        assertEquals(video, selected)
        lifecycle.onDestroy()
    }

    @Test
    fun queryChangeLoadsNewResult() = runTest {
        val repository = RecordingRepository()
        val lifecycle = LifecycleRegistry()
        lifecycle.onCreate()
        val component = DefaultSearchComponent(
            DefaultComponentContext(lifecycle),
            SearchChannelsUseCase(repository),
            StandardTestDispatcher(testScheduler),
            debounceMillis = 0
        )

        component.onQueryChanged(" Kotlin ")
        advanceUntilIdle()

        assertEquals("Kotlin", repository.queries.last())
        assertEquals("Kotlin", component.state.value.items.single().title)
        lifecycle.onDestroy()
    }

    @Test
    fun latestQueryCancelsStaleSearch() = runTest {
        val repository = CancellingRepository()
        val lifecycle = LifecycleRegistry()
        lifecycle.onCreate()
        val component = DefaultSearchComponent(
            DefaultComponentContext(lifecycle),
            SearchChannelsUseCase(repository),
            StandardTestDispatcher(testScheduler),
            debounceMillis = 0
        )

        component.onQueryChanged("slow")
        runCurrent()
        component.onQueryChanged("fast")
        repository.allowSlowToFinish.complete(Unit)
        advanceUntilIdle()

        assertEquals("fast", component.state.value.items.single().title)
        lifecycle.onDestroy()
    }

    @Test
    fun nextPagePreservesOpaqueTokenAndAppends() = runTest {
        val repository = RecordingRepository()
        val lifecycle = LifecycleRegistry()
        lifecycle.onCreate()
        val component = DefaultSearchComponent(
            DefaultComponentContext(lifecycle),
            SearchChannelsUseCase(repository),
            StandardTestDispatcher(testScheduler),
            debounceMillis = 0
        )
        component.onQueryChanged("Kotlin")
        advanceUntilIdle()

        component.loadNextPage()
        advanceUntilIdle()

        assertEquals(listOf(null, "opaque-next"), repository.tokens.takeLast(2))
        assertEquals(2, component.state.value.items.size)
        lifecycle.onDestroy()
    }

    @Test
    fun duplicateChannelFromNextPageIsIgnored() = runTest {
        val duplicate = Channel("same-id", "Same channel", "", null)
        val repository = object : SearchRepository {
            override suspend fun searchChannels(query: String, pageToken: String?): SearchResult =
                SearchResult.Success(
                    Page(
                        items = listOf(duplicate),
                        nextPageToken = if (pageToken == null) "next" else null
                    )
                )
        }
        val lifecycle = LifecycleRegistry().apply { onCreate() }
        val component = DefaultSearchComponent(
            DefaultComponentContext(lifecycle),
            SearchChannelsUseCase(repository),
            StandardTestDispatcher(testScheduler),
            debounceMillis = 0
        )

        advanceUntilIdle()
        component.loadNextPage()
        advanceUntilIdle()

        assertEquals(listOf(duplicate), component.state.value.items)
        lifecycle.onDestroy()
    }

    @Test
    fun loadingAndEmptyStatesAreExposed() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repository = object : SearchRepository {
            override suspend fun searchChannels(query: String, pageToken: String?): SearchResult {
                gate.await()
                return SearchResult.Success(Page(emptyList(), null))
            }
        }
        val lifecycle = LifecycleRegistry()
        lifecycle.onCreate()
        val component = DefaultSearchComponent(
            DefaultComponentContext(lifecycle),
            SearchChannelsUseCase(repository),
            StandardTestDispatcher(testScheduler),
            debounceMillis = 0
        )

        runCurrent()
        assertTrue(component.state.value.isLoading)
        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(component.state.value.isLoading)
        assertTrue(component.state.value.items.isEmpty())
        lifecycle.onDestroy()
    }

    @Test
    fun errorCanBeRetried() = runTest {
        var calls = 0
        val repository = object : SearchRepository {
            override suspend fun searchChannels(query: String, pageToken: String?): SearchResult {
                calls++
                return if (calls == 1) {
                    SearchResult.Failure(kg.dev.shared.feature.search.domain.model.SearchError.Network)
                } else {
                    SearchResult.Success(Page(listOf(Channel("ok", "Recovered", "", null)), null))
                }
            }
        }
        val lifecycle = LifecycleRegistry()
        lifecycle.onCreate()
        val component = DefaultSearchComponent(
            DefaultComponentContext(lifecycle),
            SearchChannelsUseCase(repository),
            StandardTestDispatcher(testScheduler),
            debounceMillis = 0
        )

        advanceUntilIdle()
        assertTrue(component.state.value.error != null)
        component.retry()
        advanceUntilIdle()
        assertEquals("Recovered", component.state.value.items.single().title)
        assertEquals(null, component.state.value.error)
        lifecycle.onDestroy()
    }

    @Test
    fun typingIsDebounced() = runTest {
        val repository = RecordingRepository()
        val lifecycle = LifecycleRegistry()
        lifecycle.onCreate()
        val component = DefaultSearchComponent(
            DefaultComponentContext(lifecycle),
            SearchChannelsUseCase(repository),
            StandardTestDispatcher(testScheduler),
            debounceMillis = 500
        )

        component.onQueryChanged("Kot")
        component.onQueryChanged("Kotlin")
        runCurrent()
        advanceTimeBy(499)
        assertTrue(repository.queries.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf("Kotlin"), repository.queries)
        lifecycle.onDestroy()
    }

    private class RecordingRepository : SearchRepository {
        val queries = mutableListOf<String>()
        val tokens = mutableListOf<String?>()
        override suspend fun searchChannels(query: String, pageToken: String?): SearchResult {
            queries += query
            tokens += pageToken
            return SearchResult.Success(
                Page(
                    items = listOf(Channel("$query-$pageToken", query, "", null)),
                    nextPageToken = if (pageToken == null) "opaque-next" else null
                )
            )
        }
    }

    private class CancellingRepository : SearchRepository {
        val allowSlowToFinish = CompletableDeferred<Unit>()
        override suspend fun searchChannels(query: String, pageToken: String?): SearchResult {
            if (query == "slow") allowSlowToFinish.await()
            return SearchResult.Success(Page(listOf(Channel(query, query, "", null)), null))
        }
    }
}
