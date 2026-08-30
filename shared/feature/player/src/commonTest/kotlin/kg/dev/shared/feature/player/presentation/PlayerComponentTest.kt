package kg.dev.shared.feature.player.presentation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kg.dev.shared.feature.history.domain.HistoryRepository
import kg.dev.shared.feature.history.domain.WatchedVideo
import kg.dev.shared.core.common.media.MediaCatalogItem
import kg.dev.shared.core.common.media.MediaProviders
import kg.dev.shared.core.common.media.MediaReference
import kg.dev.shared.feature.player.PlayableMedia
import kg.dev.shared.feature.player.ProviderMediaSurface
import kg.dev.shared.feature.player.ProviderPlaybackAdapter
import kg.dev.shared.feature.player.ProviderPlaybackAdapterRegistry
import kg.dev.shared.feature.player.ProviderPlaybackCapabilities
import kg.dev.shared.feature.player.ProviderPlaybackSession
import kg.dev.shared.feature.player.PlaybackSource
import kg.dev.shared.feature.player.PlaybackState
import kg.dev.shared.feature.player.PlayerError
import kg.dev.shared.feature.player.PlayerState
import kg.dev.shared.feature.player.VideoPlayerController
import kg.dev.shared.feature.player.library.SavedMedia
import kg.dev.shared.feature.player.library.SavedMediaRepository
import kg.dev.shared.feature.player.library.SavedMediaState
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
    fun lifecycleIsRepresentedByOneAuthoritativePlaybackState() = runTest {
        val lifecycle = LifecycleRegistry().also { it.onCreate() }
        val controller = FakeController()
        val component = component(lifecycle, controller, RecordingHistoryRepository(), StandardTestDispatcher(testScheduler))

        assertEquals(PlaybackState.Idle, component.state.value.playbackState)
        controller.publish(PlayerState(media(), PlaybackState.Loading))
        advanceUntilIdle()
        assertEquals(PlaybackState.Loading, component.state.value.playbackState)

        controller.publish(PlayerState(media(), PlaybackState.Buffering, positionMs = 1_000))
        advanceUntilIdle()
        assertEquals(PlaybackState.Buffering, component.state.value.playbackState)
        assertFalse(component.state.value.isPlaying)
        assertFalse(component.state.value.isCompleted)
        assertEquals(null, component.state.value.error)

        controller.publish(PlayerState(media(), PlaybackState.Playing, positionMs = 1_000))
        advanceUntilIdle()
        assertTrue(component.state.value.isPlaying)
        assertFalse(component.state.value.isCompleted)

        controller.publish(PlayerState(media(), PlaybackState.Error(PlayerError.NetworkFailure), positionMs = 1_000))
        advanceUntilIdle()
        assertFalse(component.state.value.isPlaying)
        assertFalse(component.state.value.isCompleted)
        assertEquals(PlayerError.NetworkFailure, component.state.value.error)
        lifecycle.onDestroy()
        advanceUntilIdle()
    }

    @Test
    fun playSeekAndProgressAreOwnedBySharedComponent() = runTest {
        val lifecycle = LifecycleRegistry().also { it.onCreate() }
        val controller = FakeController()
        val history = RecordingHistoryRepository()
        val component = component(lifecycle, controller, history, StandardTestDispatcher(testScheduler))

        component.play()
        advanceUntilIdle()
        assertEquals("media", controller.playedMedia?.catalogItem?.reference?.externalId)

        controller.publish(PlayerState(media(), PlaybackState.Playing, positionMs = 6_000, durationMs = 10_000))
        advanceUntilIdle()
        assertEquals(6_000, component.state.value.positionMs)
        assertEquals(6_000, history.saved.single().positionMs)

        component.seekTo(7_000)
        assertEquals(7_000, controller.seekedTo)
        component.pause()
        advanceUntilIdle()
        assertEquals(PlaybackState.Paused, component.state.value.playbackState)
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
    fun initialResumeSeekIsConsumedOnlyOnceAcrossPauseAndPlay() = runTest {
        val lifecycle = LifecycleRegistry().also { it.onCreate() }
        val controller = FakeController()
        val component = DefaultPlayerComponent(
            DefaultComponentContext(lifecycle), media(), controller, RecordingHistoryRepository(),
            initialPositionMs = 42_000, nowEpochMillis = { 99 },
            coroutineContext = StandardTestDispatcher(testScheduler)
        )

        component.play()
        advanceUntilIdle()
        component.pause()
        component.play()
        component.play()
        advanceUntilIdle()

        assertEquals(1, controller.loadCalls)
        assertEquals(listOf(42_000L), controller.seekCalls)
        assertEquals(2, controller.resumeCalls)
        lifecycle.onDestroy()
        advanceUntilIdle()
    }

    @Test
    fun retryPreservesCurrentSessionAndDoesNotReapplyInitialSeek() = runTest {
        val lifecycle = LifecycleRegistry().also { it.onCreate() }
        val controller = FakeController()
        val component = DefaultPlayerComponent(
            DefaultComponentContext(lifecycle), media(), controller, RecordingHistoryRepository(),
            initialPositionMs = 42_000, nowEpochMillis = { 99 },
            coroutineContext = StandardTestDispatcher(testScheduler)
        )

        component.play()
        advanceUntilIdle()
        controller.publish(
            PlayerState(media(), PlaybackState.Error(PlayerError.NetworkFailure), positionMs = 60_000, durationMs = 100_000)
        )
        advanceUntilIdle()
        component.retry()
        advanceUntilIdle()

        assertEquals(1, controller.retryCalls)
        assertEquals(listOf(42_000L), controller.seekCalls)
        assertEquals(60_000, controller.state.value.positionMs)
        lifecycle.onDestroy()
        advanceUntilIdle()
    }

    @Test
    fun completedPlaybackPersistsFinalProgressAndReplayStartsAtZero() = runTest {
        val lifecycle = LifecycleRegistry().also { it.onCreate() }
        val controller = FakeController()
        val history = RecordingHistoryRepository()
        val component = component(lifecycle, controller, history, StandardTestDispatcher(testScheduler))

        component.play()
        advanceUntilIdle()
        controller.publish(
            PlayerState(media(), PlaybackState.Completed, positionMs = 99_000, durationMs = 100_000)
        )
        advanceUntilIdle()

        assertEquals(100_000, history.saved.last().positionMs)
        assertEquals(100_000, history.saved.last().durationMs)

        component.play()
        advanceUntilIdle()
        assertEquals(0, controller.seekedTo)
        assertEquals(1, controller.resumeCalls)
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

        controller.publish(PlayerState(media(), PlaybackState.Error(PlayerError.SourceUnavailable)))
        advanceUntilIdle()
        assertEquals(PlaybackState.Error(PlayerError.SourceUnavailable), component.state.value.playbackState)

        lifecycle.onDestroy()
        advanceUntilIdle()
        assertEquals(1, controller.releaseCalls)
    }

    @Test
    fun destroyedComponentIgnoresLateControllerState() = runTest {
        val lifecycle = LifecycleRegistry().also { it.onCreate() }
        val controller = FakeController()
        val component = component(
            lifecycle, controller, RecordingHistoryRepository(), StandardTestDispatcher(testScheduler)
        )
        controller.publish(PlayerState(media(), PlaybackState.Paused, positionMs = 12_000))
        advanceUntilIdle()

        lifecycle.onDestroy()
        advanceUntilIdle()
        controller.publish(PlayerState(media(), PlaybackState.Playing, positionMs = 13_000))
        advanceUntilIdle()

        assertEquals(PlaybackState.Paused, component.state.value.playbackState)
        assertEquals(12_000, component.state.value.positionMs)
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

        assertEquals(PlaybackState.Error(PlayerError.UnsupportedMedia), component.state.value.playbackState)
        assertTrue(history.saved.isEmpty())
        lifecycle.onDestroy()
        advanceUntilIdle()
    }

    @Test
    fun providerSessionFeedsGenericStateHistoryAndControls() = runTest {
        val lifecycle = LifecycleRegistry().also { it.onCreate() }
        val controller = FakeController()
        val session = FakeProviderSession()
        val history = RecordingHistoryRepository()
        val component = DefaultPlayerComponent(
            DefaultComponentContext(lifecycle), media(providerControlled = true), controller, history,
            initialPositionMs = 42_000,
            nowEpochMillis = { 99 },
            providerPlaybackAdapters = ProviderPlaybackAdapterRegistry(listOf(FakeProviderAdapter(session))),
            coroutineContext = StandardTestDispatcher(testScheduler)
        )

        component.play()
        advanceUntilIdle()
        assertEquals(1, session.preloadCalls)
        assertEquals(0, session.loadCalls)
        assertEquals(1, session.playCalls)
        assertEquals(listOf(42_000L), session.seekCalls)
        assertEquals(0, controller.loadCalls)

        session.publish(PlayerState(media(providerControlled = true), PlaybackState.Playing, 48_000, 100_000))
        advanceUntilIdle()
        assertEquals(PlaybackState.Playing, component.state.value.playbackState)
        assertEquals(48_000, component.state.value.positionMs)
        assertEquals(48_000, history.saved.last().positionMs)

        component.pause()
        component.play()
        advanceUntilIdle()
        assertEquals(1, session.pauseCalls)
        assertEquals(2, session.playCalls)
        assertEquals(listOf(42_000L), session.seekCalls)

        session.publish(PlayerState(media(providerControlled = true), PlaybackState.Completed, 99_000, 100_000))
        advanceUntilIdle()
        assertEquals(100_000, history.saved.last().positionMs)
        component.play()
        advanceUntilIdle()
        assertEquals(0, session.seekCalls.last())

        lifecycle.onDestroy()
        advanceUntilIdle()
        assertEquals(1, session.releaseCalls)
        session.publish(PlayerState(media(providerControlled = true), PlaybackState.Playing, 8_000, 10_000))
        advanceUntilIdle()
        assertEquals(PlaybackState.Completed, component.state.value.playbackState)
    }

    @Test
    fun providerRetryDoesNotReapplyInitialSeek() = runTest {
        val lifecycle = LifecycleRegistry().also { it.onCreate() }
        val session = FakeProviderSession()
        val component = DefaultPlayerComponent(
            DefaultComponentContext(lifecycle), media(providerControlled = true), FakeController(), RecordingHistoryRepository(),
            initialPositionMs = 42_000,
            nowEpochMillis = { 99 },
            providerPlaybackAdapters = ProviderPlaybackAdapterRegistry(listOf(FakeProviderAdapter(session))),
            coroutineContext = StandardTestDispatcher(testScheduler)
        )

        component.play()
        advanceUntilIdle()
        session.publish(PlayerState(media(providerControlled = true), PlaybackState.Error(PlayerError.NetworkFailure), 60_000, 100_000))
        advanceUntilIdle()
        component.retry()
        advanceUntilIdle()

        assertEquals(1, session.retryCalls)
        assertEquals(listOf(42_000L), session.seekCalls)
        lifecycle.onDestroy()
        advanceUntilIdle()
    }

    @Test
    fun savedMediaStateIsReactiveAndCommandsRemainIndependent() = runTest {
        val lifecycle = LifecycleRegistry().also { it.onCreate() }
        val controller = FakeController()
        val saved = RecordingSavedMediaRepository()
        val component = DefaultPlayerComponent(
            DefaultComponentContext(lifecycle), media(), controller, RecordingHistoryRepository(),
            savedMediaRepository = saved, nowEpochMillis = { 99 },
            coroutineContext = StandardTestDispatcher(testScheduler)
        )

        advanceUntilIdle()
        assertFalse(component.state.value.isFavorite)
        assertFalse(component.state.value.isWatchLater)
        saved.emit(media().catalogItem.reference, SavedMediaState(isFavorite = true))
        advanceUntilIdle()
        assertTrue(component.state.value.isFavorite)
        assertFalse(component.state.value.isWatchLater)
        saved.emit(media().catalogItem.reference, SavedMediaState(isFavorite = true, isWatchLater = true))
        advanceUntilIdle()
        assertTrue(component.state.value.isWatchLater)

        component.setFavorite(false)
        advanceUntilIdle()
        component.setWatchLater(false)
        advanceUntilIdle()
        assertEquals(listOf(false), saved.favoriteWrites.map { it.second })
        assertEquals(listOf(false), saved.watchLaterWrites.map { it.second })

        lifecycle.onDestroy()
        advanceUntilIdle()
        saved.emit(media().catalogItem.reference, SavedMediaState())
        advanceUntilIdle()
        assertTrue(component.state.value.isFavorite)
    }

    @Test fun initialFavoriteOnlySavedStateIsPresented() = runTest { assertInitialSavedState(SavedMediaState(true, false), true, false) }
    @Test fun initialWatchLaterOnlySavedStateIsPresented() = runTest { assertInitialSavedState(SavedMediaState(false, true), false, true) }
    @Test fun initialBothSavedStateIsPresented() = runTest { assertInitialSavedState(SavedMediaState(true, true), true, true) }

    @Test
    fun failedSavedMediaWriteDoesNotCancelPlayerObservation() = runTest {
        val lifecycle = LifecycleRegistry().also { it.onCreate() }
        val saved = RecordingSavedMediaRepository(throwOnFavorite = true)
        val component = DefaultPlayerComponent(DefaultComponentContext(lifecycle), media(), FakeController(), RecordingHistoryRepository(),
            savedMediaRepository = saved, nowEpochMillis = { 99 }, coroutineContext = StandardTestDispatcher(testScheduler))
        component.setFavorite(true); advanceUntilIdle()
        saved.emit(media().catalogItem.reference, SavedMediaState(isWatchLater = true)); advanceUntilIdle()
        assertFalse(component.state.value.isFavorite); assertTrue(component.state.value.isWatchLater)
        lifecycle.onDestroy()
    }

    @Test
    fun playbackHistoryAndSavedMediaActionsRemainIndependent() = runTest {
        val lifecycle = LifecycleRegistry().also { it.onCreate() }
        val history = RecordingHistoryRepository()
        val saved = RecordingSavedMediaRepository(SavedMediaState(isWatchLater = true))
        val controller = FakeController()
        val component = DefaultPlayerComponent(DefaultComponentContext(lifecycle), media(), controller, history,
            savedMediaRepository = saved, nowEpochMillis = { 99 }, coroutineContext = StandardTestDispatcher(testScheduler))
        component.play(); advanceUntilIdle()
        controller.publish(PlayerState(media(), PlaybackState.Playing, 5_000, 10_000)); advanceUntilIdle()
        assertTrue(history.saved.isNotEmpty())
        assertTrue(saved.favoriteWrites.isEmpty()); assertTrue(saved.watchLaterWrites.isEmpty())
        val historyWrites = history.saved.size
        component.setFavorite(true); component.setWatchLater(false); advanceUntilIdle()
        assertEquals(historyWrites, history.saved.size)
        saved.favoriteWrites.clear(); saved.watchLaterWrites.clear()
        controller.publish(PlayerState(media(), PlaybackState.Completed, 10_000, 10_000)); advanceUntilIdle()
        assertTrue(history.saved.size > historyWrites)
        assertTrue(saved.watchLaterWrites.isEmpty())
        lifecycle.onDestroy()
    }

    private suspend fun kotlinx.coroutines.test.TestScope.assertInitialSavedState(
        state: SavedMediaState, favorite: Boolean, watchLater: Boolean
    ) {
        val lifecycle = LifecycleRegistry().also { it.onCreate() }
        val component = DefaultPlayerComponent(DefaultComponentContext(lifecycle), media(), FakeController(), RecordingHistoryRepository(),
            savedMediaRepository = RecordingSavedMediaRepository(state), nowEpochMillis = { 99 }, coroutineContext = StandardTestDispatcher(testScheduler))
        advanceUntilIdle(); assertEquals(favorite, component.state.value.isFavorite); assertEquals(watchLater, component.state.value.isWatchLater)
        lifecycle.onDestroy()
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
        MediaCatalogItem(
            MediaReference(if (providerControlled) MediaProviders.YouTube else MediaProviders.Direct, if (providerControlled) "video" else "media"),
            "Media"
        ),
        if (providerControlled) PlaybackSource.ProviderControlled(MediaReference(MediaProviders.YouTube, "video"))
        else PlaybackSource.Direct("https://example.test/video.mp4")
    )

    private class FakeController : VideoPlayerController {
        private val mutableState = MutableStateFlow(PlayerState())
        override val state: StateFlow<PlayerState> = mutableState
        var playedMedia: PlayableMedia? = null
        var loadCalls = 0
        var resumeCalls = 0
        var retryCalls = 0
        val seekCalls = mutableListOf<Long>()
        val seekedTo: Long? get() = seekCalls.lastOrNull()
        var releaseCalls = 0

        override suspend fun play(media: PlayableMedia) {
            loadCalls++
            playedMedia = media
            mutableState.value = PlayerState(media = media, playbackState = PlaybackState.Playing)
        }

        override fun resume() {
            resumeCalls++
            mutableState.value = mutableState.value.copy(playbackState = PlaybackState.Playing)
        }
        override fun pause() { mutableState.value = mutableState.value.copy(playbackState = PlaybackState.Paused) }
        override fun seekTo(positionMs: Long) {
            seekCalls += positionMs
            mutableState.value = mutableState.value.copy(positionMs = positionMs)
        }
        override fun retry() {
            retryCalls++
            mutableState.value = mutableState.value.copy(playbackState = PlaybackState.Playing)
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

    private class RecordingSavedMediaRepository(
        private val initial: SavedMediaState = SavedMediaState(), private val throwOnFavorite: Boolean = false
    ) : SavedMediaRepository {
        private val states = mutableMapOf<MediaReference, MutableStateFlow<SavedMediaState>>()
        val favoriteWrites = mutableListOf<Pair<MediaCatalogItem, Boolean>>()
        val watchLaterWrites = mutableListOf<Pair<MediaCatalogItem, Boolean>>()
        override fun observe(reference: MediaReference) = states.getOrPut(reference) { MutableStateFlow(initial) }
        override fun favorites() = MutableStateFlow(emptyList<SavedMedia>())
        override fun watchLater() = MutableStateFlow(emptyList<SavedMedia>())
        override suspend fun setFavorite(item: MediaCatalogItem, enabled: Boolean) { if (throwOnFavorite) error("write"); favoriteWrites += item to enabled }
        override suspend fun setWatchLater(item: MediaCatalogItem, enabled: Boolean) { watchLaterWrites += item to enabled }
        fun emit(reference: MediaReference, state: SavedMediaState) { observe(reference).value = state }
    }

    private class FakeProviderAdapter(
        private val session: FakeProviderSession
    ) : ProviderPlaybackAdapter {
        override val providerId = MediaProviders.YouTube
        override fun createSession(media: PlayableMedia): ProviderPlaybackSession = session
        @Composable
        override fun Surface(
            session: ProviderPlaybackSession?,
            media: PlayableMedia,
            startPositionMs: Long,
            modifier: Modifier
        ) = Unit
    }

    private class FakeProviderSession : ProviderPlaybackSession {
        private val mutableState = MutableStateFlow(PlayerState())
        override val state: StateFlow<PlayerState> = mutableState
        override val capabilities = ProviderPlaybackCapabilities(true, true, true, true)
        var preloadCalls = 0
        var loadCalls = 0
        var playCalls = 0
        var pauseCalls = 0
        var retryCalls = 0
        var releaseCalls = 0
        val seekCalls = mutableListOf<Long>()

        override suspend fun preload(media: PlayableMedia) {
            preloadCalls++
            mutableState.value = PlayerState(media, PlaybackState.Ready)
        }
        override suspend fun load(media: PlayableMedia) {
            loadCalls++
            mutableState.value = PlayerState(media, PlaybackState.Loading)
        }
        override fun play() { playCalls++ }
        override fun pause() { pauseCalls++ }
        override fun seekTo(positionMs: Long) { seekCalls += positionMs }
        override fun retry() { retryCalls++ }
        override fun release() { releaseCalls++ }
        fun publish(value: PlayerState) { mutableState.value = value }
    }
}
