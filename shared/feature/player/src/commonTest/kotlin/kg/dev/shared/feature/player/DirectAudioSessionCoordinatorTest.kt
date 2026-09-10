package kg.dev.shared.feature.player

import kg.dev.shared.core.common.media.MediaCatalogItem
import kg.dev.shared.core.common.media.MediaProviders
import kg.dev.shared.core.common.media.MediaReference
import kg.dev.shared.feature.history.domain.HistoryRepository
import kg.dev.shared.feature.history.domain.WatchedVideo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class DirectAudioSessionCoordinatorTest {
    @Test
    fun detachedDirectAudioCompletionPersistsHistoryBeforeQueueAdvance() = runTest {
        val fixture = fixture(recordQueueAdvance = true)
        fixture.host.attachUi()
        fixture.host.emit(state(media("a"), 1, PlaybackState.Playing, 8_000, 10_000))
        advanceUntilIdle()
        fixture.host.detachUi()
        fixture.host.emit(state(media("a"), 1, PlaybackState.Completed, 9_500, 10_000))
        advanceUntilIdle()

        assertEquals(listOf("history-complete:a", "root-completion:a", "queue-open:b"), fixture.events)
        assertFalse(fixture.host.isUiAttached.value)
    }

    @Test
    fun attachedDirectAudioUsesCoordinatorAsSingleCompletionOwner() = runTest {
        val fixture = fixture()
        fixture.host.attachUi()
        fixture.host.emit(state(media("a"), 1, PlaybackState.Playing, 1_000, 10_000))
        fixture.host.emit(state(media("a"), 1, PlaybackState.Completed, 10_000, 10_000))
        advanceUntilIdle()

        assertEquals(1, fixture.history.saved.count { it.positionMs == 10_000L })
        assertEquals(1, fixture.callbacks.completions)
    }

    @Test
    fun duplicateCompletionForSameGenerationIsIgnored() = runTest {
        val fixture = fixture()
        fixture.host.emit(state(media("a"), 1, PlaybackState.Playing, 1_000, 10_000))
        fixture.host.emit(state(media("a"), 1, PlaybackState.Completed, 9_999, 10_000))
        advanceUntilIdle()
        fixture.host.emit(state(media("a"), 1, PlaybackState.Completed, 10_000, 10_000))
        advanceUntilIdle()

        assertEquals(1, fixture.history.saved.count { it.positionMs == 10_000L })
        assertEquals(1, fixture.callbacks.completions)
    }

    @Test
    fun staleCompletionFromPreviousSessionCannotAdvanceCurrentSession() = runTest {
        val fixture = fixture()
        fixture.host.emit(state(media("a"), 1, PlaybackState.Playing, 4_000, 10_000))
        fixture.host.emit(state(media("b"), 2, PlaybackState.Playing, 1_000, 20_000))
        advanceUntilIdle()
        fixture.host.emit(state(media("a"), 1, PlaybackState.Completed, 10_000, 10_000))
        advanceUntilIdle()

        assertEquals(0, fixture.callbacks.completions)
        assertEquals(0, fixture.history.saved.count { it.positionMs == 10_000L })
    }

    @Test
    fun completionAfterExplicitStopIsIgnored() = runTest {
        val fixture = fixture()
        fixture.host.emit(state(media("a"), 1, PlaybackState.Playing, 4_200, 10_000))
        advanceUntilIdle()
        fixture.coordinator.stop()
        advanceUntilIdle()
        fixture.host.emit(state(media("a"), 1, PlaybackState.Completed, 10_000, 10_000))
        advanceUntilIdle()

        assertEquals(1, fixture.host.stopCalls)
        assertEquals(listOf(4_200L), fixture.history.saved.map { it.positionMs })
        assertEquals(0, fixture.callbacks.completions)
    }

    @Test
    fun applicationCallbacksCanBeReplacedAndCleared() = runTest {
        val fixture = fixture(registerCallbacks = false)
        val old = RecordingCallbacks(fixture.events, "old")
        val current = RecordingCallbacks(fixture.events, "current")
        fixture.gateway.replace(old, old)
        fixture.gateway.replace(current, current)
        fixture.host.emit(state(media("a"), 1, PlaybackState.Completed, 10_000, 10_000))
        advanceUntilIdle()
        fixture.gateway.clear(current)
        fixture.host.emit(state(media("b"), 2, PlaybackState.Completed, 20_000, 20_000))
        advanceUntilIdle()

        assertEquals(0, old.completions)
        assertEquals(1, current.completions)
    }

    @Test
    fun manualNextPersistsActualProgressBeforeQueueTransition() = runTest {
        val fixture = fixture()
        fixture.host.emit(state(media("a"), 1, PlaybackState.Playing, 42_000, 100_000))
        advanceUntilIdle()
        fixture.coordinator.next()
        advanceUntilIdle()

        assertEquals(listOf("history-progress:a:42000", "root-next"), fixture.events)
        assertEquals(0, fixture.callbacks.completions)
    }

    @Test
    fun manualPreviousPersistsActualProgressBeforeQueueTransition() = runTest {
        val fixture = fixture()
        fixture.host.emit(state(media("a"), 1, PlaybackState.Playing, 42_000, 100_000))
        advanceUntilIdle()
        fixture.coordinator.previous()
        advanceUntilIdle()

        assertEquals(listOf("history-progress:a:42000", "root-previous"), fixture.events)
        assertEquals(0, fixture.callbacks.completions)
    }

    @Test
    fun pauseTransitionPersistsProgressOnlyOnce() = runTest {
        val fixture = fixture()
        fixture.host.emit(state(media("a"), 1, PlaybackState.Playing, 3_000, 10_000))
        advanceUntilIdle()
        fixture.host.emit(state(media("a"), 1, PlaybackState.Paused, 4_000, 10_000))
        advanceUntilIdle()
        fixture.host.emit(state(media("a"), 1, PlaybackState.Paused, 4_000, 10_000))
        advanceUntilIdle()

        assertEquals(listOf(4_000L), fixture.history.saved.map { it.positionMs })
    }

    @Test
    fun interruptionPausePersistsProgressWithoutCompletionOrQueueAdvance() = runTest {
        val fixture = fixture(recordQueueAdvance = true)
        fixture.host.emit(state(media("a"), 1, PlaybackState.Playing, 4_000, 10_000))
        advanceUntilIdle()
        fixture.host.emit(state(media("a"), 1, PlaybackState.Paused, 4_200, 10_000))
        advanceUntilIdle()

        assertEquals(listOf("history-progress:a:4200"), fixture.events)
        assertEquals(0, fixture.callbacks.completions)
    }

    @Test
    fun interruptionPauseDoesNotPreventLaterNaturalCompletionForCurrentGeneration() = runTest {
        val fixture = fixture(recordQueueAdvance = true)
        fixture.host.emit(state(media("a"), 1, PlaybackState.Playing, 8_000, 10_000))
        advanceUntilIdle()
        fixture.host.emit(state(media("a"), 1, PlaybackState.Paused, 8_200, 10_000))
        advanceUntilIdle()
        fixture.host.emit(state(media("a"), 1, PlaybackState.Playing, 8_200, 10_000))
        advanceUntilIdle()
        fixture.host.emit(state(media("a"), 1, PlaybackState.Completed, 10_000, 10_000))
        advanceUntilIdle()

        assertEquals(
            listOf("history-progress:a:8200", "history-complete:a", "root-completion:a", "queue-open:b"),
            fixture.events,
        )
        assertEquals(1, fixture.callbacks.completions)
    }

    @Test
    fun absentApplicationOwnerPersistsCompletionWithoutFabricatingQueue() = runTest {
        val fixture = fixture(registerCallbacks = false)
        fixture.host.emit(state(media("a"), 1, PlaybackState.Playing, 8_000, 10_000))
        fixture.host.emit(state(media("a"), 1, PlaybackState.Completed, 10_000, 10_000))
        advanceUntilIdle()
        assertEquals(1, fixture.history.saved.size)
        assertEquals(0, fixture.callbacks.completions)
        assertFalse(fixture.gateway.next())
        assertFalse(fixture.gateway.previous())
        assertFalse(fixture.gateway.stop())
        fixture.coordinator.requestStop()
        advanceUntilIdle()

        assertEquals(1, fixture.host.stopCalls)
    }

    private fun kotlinx.coroutines.test.TestScope.fixture(
        registerCallbacks: Boolean = true,
        recordQueueAdvance: Boolean = false,
    ): Fixture {
        val events = mutableListOf<String>()
        val host = FakeDirectHost()
        val history = RecordingHistory(events)
        val gateway = DirectAudioApplicationCallbackGateway()
        val callbacks = RecordingCallbacks(events, recordQueueAdvance = recordQueueAdvance)
        if (registerCallbacks) gateway.replace(callbacks, callbacks)
        return Fixture(
            host,
            history,
            gateway,
            callbacks,
            events,
            DirectAudioSessionCoordinator(host, history, gateway, { 99 }, StandardTestDispatcher(testScheduler)),
        )
    }

    private data class Fixture(
        val host: FakeDirectHost,
        val history: RecordingHistory,
        val gateway: DirectAudioApplicationCallbackGateway,
        val callbacks: RecordingCallbacks,
        val events: MutableList<String>,
        val coordinator: DirectAudioSessionCoordinator,
    )

    private class FakeDirectHost : DirectPlaybackHost {
        override val state = MutableStateFlow(PlayerState())
        override val isUiAttached = MutableStateFlow(false)
        override val capabilities = DirectPlaybackHostCapabilities(true, true)
        var stopCalls = 0
        override fun attachUi() { isUiAttached.value = true }
        override fun detachUi() { isUiAttached.value = false }
        override suspend fun play(media: PlayableMedia) = Unit
        override fun play() = Unit
        override fun pause() = Unit
        override fun seekTo(positionMs: Long) = Unit
        override fun stop() { stopCalls++ }
        fun emit(value: PlayerState) { state.value = value }
    }

    private class RecordingHistory(private val events: MutableList<String>) : HistoryRepository {
        val saved = mutableListOf<WatchedVideo>()
        override suspend fun save(video: WatchedVideo) {
            saved += video
            events += if (video.durationMs != null && video.positionMs == video.durationMs) {
                "history-complete:${video.reference.externalId}"
            } else {
                "history-progress:${video.reference.externalId}:${video.positionMs}"
            }
        }
        override suspend fun recent(limit: Long) = saved
        override suspend fun delete(reference: MediaReference) = Unit
    }

    private class RecordingCallbacks(
        private val events: MutableList<String>,
        private val name: String = "root",
        private val recordQueueAdvance: Boolean = false,
    ) : DirectAudioApplicationCallbacks {
        var completions = 0
        override suspend fun onNaturalCompletion(reference: MediaReference) {
            completions++
            events += "$name-completion:${reference.externalId}"
            if (recordQueueAdvance) events += "queue-open:b"
        }
        override fun onNext() { events += "$name-next" }
        override fun onPrevious() { events += "$name-previous" }
        override fun onStop() { events += "$name-stop" }
    }

    private fun media(id: String) = PlayableMedia(
        MediaCatalogItem(MediaReference(MediaProviders.Direct, id), id.uppercase()),
        PlaybackSource.Direct("https://example.test/$id.mp3", "audio/mpeg"),
    )

    private fun state(media: PlayableMedia, generation: Long, playbackState: PlaybackState, position: Long, duration: Long) =
        PlayerState(media, playbackState, position, duration, sessionGeneration = generation)
}
