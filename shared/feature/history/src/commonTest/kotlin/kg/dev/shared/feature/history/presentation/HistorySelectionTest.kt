package kg.dev.shared.feature.history.presentation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import kg.dev.shared.core.common.media.MediaProviders
import kg.dev.shared.core.common.media.MediaReference
import kg.dev.shared.feature.history.domain.HistoryRepository
import kg.dev.shared.feature.history.domain.ResumeDecision
import kg.dev.shared.feature.history.domain.WatchedVideo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class HistorySelectionTest {
    @Test
    fun selectionEmitsDurableMediaReferenceForRootOwnedNavigation() = runTest {
        val lifecycle = LifecycleRegistry().also { it.onCreate() }
        var selected: HistoryItemUiModel? = null
        val component = DefaultHistoryComponent(
            DefaultComponentContext(lifecycle),
            repository = FakeHistoryRepository,
            onItemSelected = { selected = it },
            coroutineContext = StandardTestDispatcher(testScheduler)
        )
        advanceUntilIdle()

        val item = component.state.value.items.single()
        component.select(item)

        assertEquals(MediaReference(MediaProviders.YouTube, "video"), selected?.reference)
        assertEquals(12_000, selected?.positionMs)
        assertEquals(12_000, selected?.startPositionMs)
        lifecycle.onDestroy()
    }

    @Test
    fun historyPresentationDistinguishesResumableCompletedAndUnknownDurationItems() = runTest {
        val lifecycle = LifecycleRegistry().also { it.onCreate() }
        val component = DefaultHistoryComponent(
            DefaultComponentContext(lifecycle),
            repository = MultipleHistoryRepository,
            coroutineContext = StandardTestDispatcher(testScheduler)
        )
        advanceUntilIdle()

        val byId = component.state.value.items.associateBy { it.reference.externalId }
        assertEquals(20_000, byId.getValue("unfinished").startPositionMs)
        assertIs<ResumeDecision.ResumeFrom>(byId.getValue("unfinished").resumeDecision)
        assertEquals(0, byId.getValue("completed").startPositionMs)
        assertIs<ResumeDecision.Completed>(byId.getValue("completed").resumeDecision)
        assertEquals(42_000, byId.getValue("unknown-duration").startPositionMs)
        assertIs<ResumeDecision.ResumeFrom>(byId.getValue("unknown-duration").resumeDecision)
        lifecycle.onDestroy()
    }

    private object FakeHistoryRepository : HistoryRepository {
        override suspend fun save(video: WatchedVideo) = Unit
        override suspend fun recent(limit: Long) = listOf(
            WatchedVideo(
                reference = MediaReference(MediaProviders.YouTube, "video"),
                title = "Video",
                thumbnailUrl = null,
                durationMs = 60_000,
                positionMs = 12_000,
                watchedAtEpochMs = 1
            )
        )
        override suspend fun delete(reference: MediaReference) = Unit
    }

    private object MultipleHistoryRepository : HistoryRepository {
        override suspend fun save(video: WatchedVideo) = Unit
        override suspend fun recent(limit: Long) = listOf(
            watched("unfinished", 20_000, 100_000),
            watched("completed", 95_000, 100_000),
            watched("unknown-duration", 42_000, null)
        )
        override suspend fun delete(reference: MediaReference) = Unit

        private fun watched(id: String, positionMs: Long, durationMs: Long?) = WatchedVideo(
            reference = MediaReference(MediaProviders.Direct, id),
            title = id,
            positionMs = positionMs,
            durationMs = durationMs,
            watchedAtEpochMs = 1
        )
    }
}
