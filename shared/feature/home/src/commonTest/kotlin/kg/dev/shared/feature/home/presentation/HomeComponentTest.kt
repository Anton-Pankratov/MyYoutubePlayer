package kg.dev.shared.feature.home.presentation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import kg.dev.shared.core.common.media.MediaProviders
import kg.dev.shared.core.common.media.MediaReference
import kg.dev.shared.feature.history.domain.HistoryRepository
import kg.dev.shared.feature.history.domain.WatchedVideo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HomeComponentTest {
    @Test
    fun emptyHistoryProducesEmptyHome() = runTest {
        val component = component(FakeHistoryRepository(emptyList()))
        advanceUntilIdle()

        assertTrue(component.state.value.continueWatching.isEmpty())
        assertTrue(component.state.value.recentlyWatched.isEmpty())
    }

    @Test
    fun derivesContinueWatchingFromCentralizedResumePolicyAndKeepsRecentOrdering() = runTest {
        val videos = listOf(
            watched("unstarted", 0, 100_000, watchedAt = 6),
            watched("unfinished", 42_000, 180_000, watchedAt = 5),
            watched("completed", 171_000, 180_000, watchedAt = 4),
            watched("unknown", 42_000, null, watchedAt = 3),
            watched("legacy", 20_000, 100_000, watchedAt = 2)
        )
        val component = component(
            repository = FakeHistoryRepository(videos),
            availability = HomeMediaAvailability { it.externalId != "legacy" }
        )
        advanceUntilIdle()

        assertEquals(listOf("unfinished", "unknown"), component.state.value.continueWatching.map { it.reference.externalId })
        assertEquals(listOf("unstarted", "unfinished", "completed", "unknown", "legacy"), component.state.value.recentlyWatched.map { it.reference.externalId })
        assertFalse(component.state.value.recentlyWatched.first { it.reference.externalId == "legacy" }.isAvailable)
    }

    @Test
    fun boundsRecentListAndSelectsOnlyAvailableItemsWithResolvedPosition() = runTest {
        val videos = (1..12).map { index ->
            watched("video-$index", if (index == 1) 95_000 else 10_000, 100_000, watchedAt = (20 - index).toLong())
        }
        var selected: HomeMediaItemUiModel? = null
        val repository = FakeHistoryRepository(videos)
        val component = component(repository, onSelected = { selected = it })
        advanceUntilIdle()

        assertEquals(50, repository.requestedLimit)
        assertEquals(10, component.state.value.recentlyWatched.size)
        val unfinished = component.state.value.continueWatching.first()
        component.select(unfinished)
        assertEquals(10_000, selected?.startPositionMs)

        val completed = component.state.value.recentlyWatched.first { it.reference.externalId == "video-1" }
        component.select(completed)
        assertEquals(0, selected?.startPositionMs)
    }

    private fun kotlinx.coroutines.test.TestScope.component(
        repository: FakeHistoryRepository,
        availability: HomeMediaAvailability = HomeMediaAvailability { true },
        onSelected: (HomeMediaItemUiModel) -> Unit = {}
    ): DefaultHomeComponent {
        val lifecycle = LifecycleRegistry().also { it.onCreate() }
        return DefaultHomeComponent(
            componentContext = DefaultComponentContext(lifecycle),
            historyRepository = repository,
            mediaAvailability = availability,
            onItemSelected = onSelected,
            coroutineContext = StandardTestDispatcher(testScheduler)
        )
    }

    private fun watched(id: String, positionMs: Long, durationMs: Long?, watchedAt: Long) = WatchedVideo(
        reference = MediaReference(MediaProviders.Direct, id),
        title = id,
        positionMs = positionMs,
        durationMs = durationMs,
        watchedAtEpochMs = watchedAt
    )

    private class FakeHistoryRepository(private val videos: List<WatchedVideo>) : HistoryRepository {
        var requestedLimit: Long? = null
        override suspend fun save(video: WatchedVideo) = Unit
        override suspend fun recent(limit: Long): List<WatchedVideo> {
            requestedLimit = limit
            return videos
        }
        override suspend fun delete(reference: MediaReference) = Unit
    }
}
