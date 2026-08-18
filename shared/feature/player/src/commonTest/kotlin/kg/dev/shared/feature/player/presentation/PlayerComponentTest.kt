package kg.dev.shared.feature.player.presentation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import kg.dev.shared.feature.history.domain.HistoryRepository
import kg.dev.shared.feature.history.domain.WatchedVideo
import kg.dev.shared.core.common.media.MediaCatalogItem
import kg.dev.shared.core.common.media.MediaProviders
import kg.dev.shared.core.common.media.MediaReference
import kg.dev.shared.feature.player.PlayableMedia
import kg.dev.shared.feature.player.PlaybackSource
import kg.dev.shared.feature.player.PlayerError
import kg.dev.shared.feature.player.PlayerState
import kg.dev.shared.feature.player.VideoPlayerController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerComponentTest {
    @Test
    fun playSeekAndProgressAreOwnedBySharedComponent() = runTest {
        val lifecycle = LifecycleRegistry().also { it.onCreate() }
        val controller = FakeController()
        val history = RecordingHistoryRepository()
        val component = component(lifecycle, controller, history, StandardTestDispatcher(testScheduler))

        component.play()
        advanceUntilIdle()
        assertEquals("media", controller.playedMedia?.catalogItem?.reference?.externalId)

        controller.publish(PlayerState(media(), isPlaying = true, positionMs = 6_000, durationMs = 10_000))
        advanceUntilIdle()
        assertEquals(6_000, component.state.value.positionMs)
        assertEquals(6_000, history.saved.single().positionMs)

        component.seekTo(7_000)
        assertEquals(7_000, controller.seekedTo)
        component.pause()
        advanceUntilIdle()
        assertFalse(component.state.value.isPlaying)
        assertTrue(history.saved.isNotEmpty())
        lifecycle.onDestroy()
    }

    @Test
    fun restorePositionIsAppliedAfterPlaybackStarts() = runTest {
        val lifecycle = LifecycleRegistry().also { it.onCreate() }
        val controller = FakeController()
        val component = DefaultPlayerComponent(
            DefaultComponentContext(lifecycle), media(), controller, RecordingHistoryRepository(),
            initialPositionMs = 1_234, nowEpochMillis = { 99 },
            coroutineContext = StandardTestDispatcher(testScheduler)
        )

        component.play()
        advanceUntilIdle()

        assertEquals(1_234, controller.seekedTo)
        lifecycle.onDestroy()
        advanceUntilIdle()
    }

    @Test
    fun errorsAndLifecycleReleaseArePropagated() = runTest {
        val lifecycle = LifecycleRegistry().also { it.onCreate() }
        val controller = FakeController()
        val component = component(
            lifecycle, controller, RecordingHistoryRepository(), StandardTestDispatcher(testScheduler)
        )

        controller.publish(PlayerState(media(), error = PlayerError.SourceUnavailable))
        advanceUntilIdle()
        assertEquals(PlayerError.SourceUnavailable, component.state.value.error)

        lifecycle.onDestroy()
        advanceUntilIdle()
        assertEquals(1, controller.releaseCalls)
    }

    @Test
    fun providerControlledMediaNeverUsesNativeControllerOrCreatesHistory() = runTest {
        val lifecycle = LifecycleRegistry().also { it.onCreate() }
        val controller = FakeController()
        val history = RecordingHistoryRepository()
        val component = DefaultPlayerComponent(
            DefaultComponentContext(lifecycle), media(providerControlled = true), controller, history,
            nowEpochMillis = { 99 }, coroutineContext = StandardTestDispatcher(testScheduler)
        )

        component.play()
        advanceUntilIdle()

        assertEquals(PlayerError.UnsupportedMedia, component.state.value.error)
        assertTrue(history.saved.isEmpty())
        lifecycle.onDestroy()
        advanceUntilIdle()
    }

    private fun component(
        lifecycle: LifecycleRegistry,
        controller: FakeController,
        history: RecordingHistoryRepository,
        coroutineContext: CoroutineContext
    ) = DefaultPlayerComponent(
        DefaultComponentContext(lifecycle), media(), controller, history,
        nowEpochMillis = { 99 }, coroutineContext = coroutineContext
    )

    private fun media(providerControlled: Boolean = false) = PlayableMedia(
        MediaCatalogItem(MediaReference(MediaProviders.Direct, "media"), "Media"),
        if (providerControlled) PlaybackSource.ProviderControlled(MediaReference(MediaProviders.YouTube, "video"))
        else PlaybackSource.Direct("https://example.test/video.mp4")
    )

    private class FakeController : VideoPlayerController {
        private val mutableState = MutableStateFlow(PlayerState())
        override val state: StateFlow<PlayerState> = mutableState
        var playedMedia: PlayableMedia? = null
        var seekedTo: Long? = null
        var releaseCalls = 0

        override suspend fun play(media: PlayableMedia) {
            playedMedia = media
            mutableState.value = PlayerState(media = media, isPlaying = true)
        }

        override fun pause() { mutableState.value = mutableState.value.copy(isPlaying = false) }
        override fun seekTo(positionMs: Long) {
            seekedTo = positionMs
            mutableState.value = mutableState.value.copy(positionMs = positionMs)
        }
        override fun release() { releaseCalls++ }
        fun publish(state: PlayerState) { mutableState.value = state }
    }

    private class RecordingHistoryRepository : HistoryRepository {
        val saved = mutableListOf<WatchedVideo>()
        override suspend fun save(video: WatchedVideo) { saved += video }
        override suspend fun recent(limit: Long): List<WatchedVideo> = saved
        override suspend fun delete(reference: MediaReference) = Unit
    }
}
