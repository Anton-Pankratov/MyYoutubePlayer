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
import kg.dev.shared.core.ui.navigation.PlaybackQueueState
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
import kg.dev.shared.feature.player.DirectPlaybackHost
import kg.dev.shared.feature.player.DirectPlaybackHostCapabilities
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
    fun fullscreenPresentationStartsInlineAndEnterExitAreIdempotent() = runTest {
        val lifecycle = LifecycleRegistry().also { it.onCreate() }
        val controller = FakeController()
        val component = component(lifecycle, controller, RecordingHistoryRepository(), StandardTestDispatcher(testScheduler))

        assertTrue(component.canPresentFullscreen)
        assertEquals(PlayerDisplayMode.Inline, component.state.value.displayMode)
        component.requestFullscreen(); component.requestFullscreen()
        assertEquals(PlayerDisplayMode.Fullscreen, component.state.value.displayMode)

        controller.publish(PlayerState(media(), PlaybackState.Error(PlayerError.NetworkFailure)))
        advanceUntilIdle()
        assertEquals(PlayerDisplayMode.Fullscreen, component.state.value.displayMode)
        component.exitFullscreen(); component.exitFullscreen()
        assertEquals(PlayerDisplayMode.Inline, component.state.value.displayMode)
        lifecycle.onDestroy()
    }

    @Test
    fun fullscreenCapabilityIsProviderNeutralAndExcludesDirectAudio() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val audioLifecycle = LifecycleRegistry().also { it.onCreate() }
        val audio = PlayableMedia(media().catalogItem, PlaybackSource.Direct("https://example.test/audio", "audio/mpeg"))
        val audioComponent = DefaultPlayerComponent(
            DefaultComponentContext(audioLifecycle), audio, FakeController(), RecordingHistoryRepository(),
            nowEpochMillis = { 99 }, coroutineContext = dispatcher,
        )
        assertFalse(audioComponent.canPresentFullscreen)
        audioComponent.requestFullscreen()
        assertEquals(PlayerDisplayMode.Inline, audioComponent.state.value.displayMode)

        val providerLifecycle = LifecycleRegistry().also { it.onCreate() }
        val providerSession = FakeProviderSession(supportsFullscreenPresentation = true)
        val provider = DefaultPlayerComponent(
            DefaultComponentContext(providerLifecycle), media(providerControlled = true), FakeController(), RecordingHistoryRepository(),
            nowEpochMillis = { 99 },
            providerPlaybackAdapters = ProviderPlaybackAdapterRegistry(listOf(FakeProviderAdapter(providerSession))),
            coroutineContext = dispatcher,
        )
        advanceUntilIdle()
        assertTrue(provider.canPresentFullscreen)
        provider.requestFullscreen()
        assertEquals(PlayerDisplayMode.Fullscreen, provider.state.value.displayMode)
        audioLifecycle.onDestroy(); providerLifecycle.onDestroy()
    }

    @Test
    fun mediaReplacementForegroundRestoreAndDestructionStartOrEndInline() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val firstLifecycle = LifecycleRegistry().also { it.onCreate() }
        val first = component(firstLifecycle, FakeController(), RecordingHistoryRepository(), dispatcher)
        first.requestFullscreen()
        assertEquals(PlayerDisplayMode.Fullscreen, first.state.value.displayMode)

        firstLifecycle.onDestroy()
        assertEquals(PlayerDisplayMode.Inline, first.state.value.displayMode)

        val replacementLifecycle = LifecycleRegistry().also { it.onCreate() }
        val replacement = DefaultPlayerComponent(
            DefaultComponentContext(replacementLifecycle), media(), FakeController(), RecordingHistoryRepository(),
            nowEpochMillis = { 99 }, coroutineContext = dispatcher,
        )
        // Root queue replacement and ForegroundRequired restoration both create a new Player route.
        assertEquals(PlayerDisplayMode.Inline, replacement.state.value.displayMode)
        replacementLifecycle.onDestroy()
    }

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
    fun completedPlaybackPersistsHistoryBeforeQueueNotificationExactlyOnce() = runTest {
        val lifecycle = LifecycleRegistry().also { it.onCreate() }
        val events = mutableListOf<String>()
        val history = RecordingHistoryRepository { events += "history-save" }
        var completionCalls = 0
        val component = DefaultPlayerComponent(
            DefaultComponentContext(lifecycle), media(), FakeController(), history,
            nowEpochMillis = { 99 },
            onNaturalCompletion = { events += "queue-completion"; completionCalls++ },
            coroutineContext = StandardTestDispatcher(testScheduler)
        )
        val controller = component.videoPlayerController as FakeController

        controller.publish(PlayerState(media(), PlaybackState.Completed, positionMs = 99_000, durationMs = 100_000))
        advanceUntilIdle()
        controller.publish(PlayerState(media(), PlaybackState.Completed, positionMs = 98_000, durationMs = 100_000))
        advanceUntilIdle()

        assertEquals(listOf("history-save", "queue-completion"), events)
        assertEquals(1, completionCalls)
        lifecycle.onDestroy()
        advanceUntilIdle()
    }

    @Test
    fun queueControlsReflectActiveQueueAndDelegateCallbacks() = runTest {
        val items = listOf(media().catalogItem, item("youtube", "middle"), item("direct", "last"))
        val queue = MutableStateFlow(
            PlaybackQueueState(items = items, currentIndex = 0)
        )
        var next = 0
        var previous = 0
        fun componentFor(index: Int): DefaultPlayerComponent = DefaultPlayerComponent(
            DefaultComponentContext(LifecycleRegistry().also { it.onCreate() }), playable(items[index]), FakeController(), RecordingHistoryRepository(),
            nowEpochMillis = { 99 }, playbackQueue = queue, onQueueNext = { next++ }, onQueuePrevious = { previous++ },
            coroutineContext = StandardTestDispatcher(testScheduler)
        )
        val component = componentFor(0)
        advanceUntilIdle()
        assertEquals(QueueControls(0, 3, hasPrevious = false, hasNext = true), component.queueControls.value)
        queue.value = queue.value.copy(currentIndex = 1)
        val middle = componentFor(1)
        advanceUntilIdle()
        assertEquals(QueueControls(1, 3, hasPrevious = true, hasNext = true), middle.queueControls.value)
        queue.value = queue.value.copy(currentIndex = 2)
        val last = componentFor(2)
        advanceUntilIdle()
        assertEquals(QueueControls(2, 3, hasPrevious = true, hasNext = false), last.queueControls.value)
        middle.previousQueueItem(); middle.nextQueueItem()
        assertEquals(1, previous); assertEquals(1, next)
    }

    @Test
    fun queueTransitionLifecyclePersistsActualProgressWithoutSyntheticCompletion() = runTest {
        val lifecycle = LifecycleRegistry().also { it.onCreate() }
        val controller = FakeController()
        val history = RecordingHistoryRepository()
        var completions = 0
        DefaultPlayerComponent(
            DefaultComponentContext(lifecycle), media(), controller, history,
            nowEpochMillis = { 99 }, onNaturalCompletion = { completions++ },
            coroutineContext = StandardTestDispatcher(testScheduler)
        )

        controller.publish(PlayerState(media(), PlaybackState.Playing, positionMs = 42_000, durationMs = 100_000))
        advanceUntilIdle()
        lifecycle.onDestroy()
        advanceUntilIdle()

        assertEquals(42_000, history.saved.last().positionMs)
        assertEquals(100_000, history.saved.last().durationMs)
        assertEquals(0, completions)
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

    @Test
    fun directHostOwnsEligibleAudioWithoutStartingLegacyController() = runTest {
        val lifecycle = LifecycleRegistry().also { it.onCreate() }
        val legacy = FakeController()
        val host = FakeDirectHost()
        val audio = PlayableMedia(media().catalogItem, PlaybackSource.Direct("https://example.test/audio", "audio/mpeg"))
        val component = DefaultPlayerComponent(
            DefaultComponentContext(lifecycle), audio, legacy, RecordingHistoryRepository(),
            nowEpochMillis = { 99 }, directPlaybackHost = host,
            coroutineContext = StandardTestDispatcher(testScheduler),
        )

        component.play(); advanceUntilIdle()
        lifecycle.onDestroy(); advanceUntilIdle()

        assertEquals(1, host.startCalls)
        assertEquals(0, legacy.loadCalls)
        assertEquals(1, host.detachCalls)
        assertEquals(0, legacy.releaseCalls)
    }

    @Test
    fun serviceHostedAudioHistoryAndCompletionAreNotOwnedByPlayerComponent() = runTest {
        val lifecycle = LifecycleRegistry().also { it.onCreate() }
        val host = FakeDirectHost()
        val history = RecordingHistoryRepository()
        var completions = 0
        val audio = PlayableMedia(media().catalogItem, PlaybackSource.Direct("https://example.test/audio", "audio/mpeg"))
        DefaultPlayerComponent(
            DefaultComponentContext(lifecycle), audio, FakeController(), history,
            nowEpochMillis = { 99 }, directPlaybackHost = host,
            onNaturalCompletion = { completions++ },
            coroutineContext = StandardTestDispatcher(testScheduler),
        )

        host.state.value = PlayerState(audio, PlaybackState.Playing, 6_000, 10_000, sessionGeneration = 1)
        advanceUntilIdle()
        host.state.value = PlayerState(audio, PlaybackState.Completed, 10_000, 10_000, sessionGeneration = 1)
        advanceUntilIdle()
        lifecycle.onDestroy()
        advanceUntilIdle()

        assertTrue(history.saved.isEmpty())
        assertEquals(0, completions)
        assertEquals(1, host.detachCalls)
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
        else PlaybackSource.Direct("https://example.test/video.mp4", "video/mp4")
    )

    private fun item(provider: String, externalId: String) = MediaCatalogItem(
        MediaReference(kg.dev.shared.core.common.media.MediaProviderId(provider), externalId), "Media $externalId"
    )

    private fun playable(item: MediaCatalogItem) = PlayableMedia(
        item,
        if (item.reference.provider == MediaProviders.Direct) PlaybackSource.Direct("https://example.test/${item.reference.externalId}.mp4", "video/mp4")
        else PlaybackSource.ProviderControlled(item.reference)
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

    private class FakeDirectHost : DirectPlaybackHost {
        override val state = MutableStateFlow(PlayerState())
        override val isUiAttached = MutableStateFlow(false)
        override val capabilities = DirectPlaybackHostCapabilities(true, true)
        var startCalls = 0
        var detachCalls = 0
        override fun attachUi() { isUiAttached.value = true }
        override fun detachUi() { detachCalls++; isUiAttached.value = false }
        override suspend fun play(media: PlayableMedia) { startCalls++; state.value = PlayerState(media, PlaybackState.Playing) }
        override fun play() = Unit
        override fun pause() = Unit
        override fun seekTo(positionMs: Long) = Unit
        override fun stop() = Unit
    }

    private class RecordingHistoryRepository(private val onSave: () -> Unit = {}) : HistoryRepository {
        val saved = mutableListOf<WatchedVideo>()
        override suspend fun save(video: WatchedVideo) { saved += video; onSave() }
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

    private class FakeProviderSession(
        supportsFullscreenPresentation: Boolean = false,
    ) : ProviderPlaybackSession {
        private val mutableState = MutableStateFlow(PlayerState())
        override val state: StateFlow<PlayerState> = mutableState
        override val capabilities = ProviderPlaybackCapabilities(
            canPlayPause = true,
            canSeek = true,
            reportsPosition = true,
            reportsDuration = true,
            supportsFullscreenPresentation = supportsFullscreenPresentation,
        )
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
