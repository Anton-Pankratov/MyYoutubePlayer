package kg.dev.shared.core.ui.navigation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import kg.dev.shared.core.common.media.MediaCatalogItem
import kg.dev.shared.core.common.media.MediaProviderId
import kg.dev.shared.core.common.media.MediaReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RootComponentMediaOpeningTest {
    @Test
    fun mediaSelectionNavigatesOnlyToInternalPlayerDestination() = runTest {
        val lifecycle = LifecycleRegistry().also { it.onCreate() }
        val media = item("youtube")
        val root = DefaultRootComponent(
            componentContext = DefaultComponentContext(lifecycle),
            initialConfiguration = Configuration.Search,
            searchComponentFactory = { Any() },
            mediaOpenCoordinator = object : MediaOpenCoordinator {
                override suspend fun open(item: MediaCatalogItem) = MediaOpenResult.Player(
                    Configuration.Player(
                        providerId = item.reference.provider.value,
                        externalId = item.reference.externalId,
                        title = item.title,
                        playbackKind = "provider-controlled"
                    )
                )
            },
            coroutineContext = StandardTestDispatcher(testScheduler)
        )

        root.openMedia(media, startPositionMs = 4_200)
        advanceUntilIdle()

        val player = assertIs<Configuration.Player>(root.childStack.value.active.configuration)
        assertEquals("youtube", player.providerId)
        assertEquals("video", player.externalId)
        assertEquals(4_200, player.startPositionMs)
        assertEquals(MediaOpenState.Idle, root.mediaOpenState.value)
        lifecycle.onDestroy()
    }

    @Test
    fun unavailableMediaStaysInsideApplicationState() = runTest {
        val lifecycle = LifecycleRegistry().also { it.onCreate() }
        val root = DefaultRootComponent(
            componentContext = DefaultComponentContext(lifecycle),
            initialConfiguration = Configuration.Home,
            searchComponentFactory = { Any() },
            mediaOpenCoordinator = object : MediaOpenCoordinator {
                override suspend fun open(item: MediaCatalogItem) =
                    MediaOpenResult.Failure("Unavailable in app", retryable = false)
            },
            coroutineContext = StandardTestDispatcher(testScheduler)
        )

        root.openMedia(item("unknown"))
        advanceUntilIdle()

        assertEquals(Configuration.Home, root.childStack.value.active.configuration)
        assertIs<MediaOpenState.Failed>(root.mediaOpenState.value)
        lifecycle.onDestroy()
    }

    @Test
    fun historySelectionResumesOnlyInInternalPlayerDestination() = runTest {
        val lifecycle = LifecycleRegistry().also { it.onCreate() }
        val root = DefaultRootComponent(
            componentContext = DefaultComponentContext(lifecycle),
            initialConfiguration = Configuration.Home,
            searchComponentFactory = { Any() },
            mediaOpenCoordinator = object : MediaOpenCoordinator {
                override suspend fun open(item: MediaCatalogItem) = MediaOpenResult.Player(
                    Configuration.Player(
                        providerId = item.reference.provider.value,
                        externalId = item.reference.externalId,
                        title = item.title,
                        playbackKind = "provider-controlled"
                    )
                )
            },
            coroutineContext = StandardTestDispatcher(testScheduler)
        )

        root.openMedia(item("youtube"), startPositionMs = 31_000)
        advanceUntilIdle()

        val player = assertIs<Configuration.Player>(root.childStack.value.active.configuration)
        assertEquals(31_000, player.startPositionMs)
        lifecycle.onDestroy()
    }

    @Test
    fun playAllOpensFirstItemAndSettlesSnapshotState() = runTest {
        val coordinator = ControlledCoordinator()
        val a = item("direct", "a")
        coordinator.enqueue(a)
        val root = root(coordinator)

        root.playAll(listOf(a, item("youtube", "b"), item("direct", "c")))
        advanceUntilIdle()

        assertEquals(listOf(a.reference, MediaReference(MediaProviderId("youtube"), "b"), MediaReference(MediaProviderId("direct"), "c")), root.playbackQueue.value.items.map { it.reference })
        assertNull(root.playbackQueue.value.currentIndex)
        assertEquals(0, root.playbackQueue.value.pendingIndex)
        assertEquals(listOf(a.reference), coordinator.requests.map { it.reference })

        coordinator.complete(a)
        advanceUntilIdle()

        assertEquals(0, root.playbackQueue.value.currentIndex)
        assertNull(root.playbackQueue.value.pendingIndex)
        assertEquals("a", player(root).externalId)
    }

    @Test
    fun newestQueueIntentWinsWhenEarlierResolutionCompletesLate() = runTest {
        val coordinator = ControlledCoordinator()
        val a = item("direct", "a")
        val b = item("youtube", "b")
        val c = item("direct", "c")
        coordinator.enqueue(a)
        coordinator.enqueue(b)
        coordinator.enqueue(c)
        val root = root(coordinator)

        root.playAll(listOf(a, b, c)); advanceUntilIdle(); coordinator.complete(a); advanceUntilIdle()
        root.onQueueItemCompleted(a.reference); advanceUntilIdle()
        root.queueNext(); advanceUntilIdle()
        coordinator.complete(c); advanceUntilIdle()
        coordinator.complete(b); advanceUntilIdle()

        assertEquals("c", player(root).externalId)
        assertEquals(2, root.playbackQueue.value.currentIndex)
        assertNull(root.playbackQueue.value.pendingIndex)
        assertEquals(listOf(a.reference, b.reference, c.reference), coordinator.requests.map { it.reference })
    }

    @Test
    fun standaloneOpenClearsQueueAndWinsOverLateQueueResolution() = runTest {
        val coordinator = ControlledCoordinator()
        val a = item("direct", "a")
        val b = item("youtube", "b")
        val x = item("direct", "x")
        coordinator.enqueue(a); coordinator.enqueue(b); coordinator.enqueue(x)
        val root = root(coordinator)

        root.playAll(listOf(a, b)); advanceUntilIdle(); coordinator.complete(a); advanceUntilIdle()
        root.onQueueItemCompleted(a.reference); advanceUntilIdle()
        root.openMedia(x); advanceUntilIdle(); coordinator.complete(x); advanceUntilIdle()
        coordinator.complete(b); advanceUntilIdle()

        assertFalse(root.playbackQueue.value.isActive)
        assertEquals("x", player(root).externalId)
    }

    @Test
    fun secondPlayAllReplacesEarlierQueueAndIgnoresLateResult() = runTest {
        val coordinator = ControlledCoordinator()
        val a = item("direct", "a")
        val b = item("youtube", "b")
        val x = item("direct", "x")
        val y = item("youtube", "y")
        coordinator.enqueue(a); coordinator.enqueue(b); coordinator.enqueue(x)
        val root = root(coordinator)

        root.playAll(listOf(a, b)); advanceUntilIdle(); coordinator.complete(a); advanceUntilIdle()
        root.onQueueItemCompleted(a.reference); advanceUntilIdle()
        root.playAll(listOf(x, y)); advanceUntilIdle(); coordinator.complete(x); advanceUntilIdle()
        coordinator.complete(b); advanceUntilIdle()

        assertEquals(listOf(x.reference, y.reference), root.playbackQueue.value.items.map { it.reference })
        assertEquals(0, root.playbackQueue.value.currentIndex)
        assertEquals("x", player(root).externalId)
    }

    @Test
    fun unavailableCandidatesAreSkippedOnceAndAllUnavailableQueueClears() = runTest {
        val coordinator = ControlledCoordinator()
        val a = item("direct", "a")
        val b = item("youtube", "b")
        val c = item("direct", "c")
        coordinator.enqueue(a, MediaOpenResult.Failure("gone", retryable = false))
        coordinator.enqueue(b, MediaOpenResult.Failure("gone", retryable = false))
        coordinator.enqueue(c, MediaOpenResult.Failure("gone", retryable = false))
        val root = root(coordinator)

        root.playAll(listOf(a, b, c)); advanceUntilIdle()

        assertEquals(listOf(a.reference, b.reference, c.reference), coordinator.requests.map { it.reference })
        assertFalse(root.playbackQueue.value.isActive)
        assertEquals(Configuration.Home, root.childStack.value.active.configuration)
    }

    @Test
    fun retryableQueueFailureDoesNotRequestLaterCandidate() = runTest {
        val coordinator = ControlledCoordinator()
        val a = item("direct", "a")
        val b = item("youtube", "b")
        val c = item("direct", "c")
        coordinator.enqueue(a); coordinator.enqueue(b, MediaOpenResult.Failure("retry", retryable = true))
        val root = root(coordinator)

        root.playAll(listOf(a, b, c)); advanceUntilIdle(); coordinator.complete(a); advanceUntilIdle()
        root.queueNext(); advanceUntilIdle()

        assertEquals(listOf(a.reference, b.reference), coordinator.requests.map { it.reference })
        assertEquals(0, root.playbackQueue.value.currentIndex)
        assertEquals(1, root.playbackQueue.value.pendingIndex)
        assertIs<MediaOpenState.Failed>(root.mediaOpenState.value)
    }

    @Test
    fun duplicateLateUnrelatedAndClearedCompletionsDoNotCreateExtraRequests() = runTest {
        val coordinator = ControlledCoordinator(); val a = item("direct", "a"); val b = item("youtube", "b")
        coordinator.enqueue(a); coordinator.enqueue(b)
        val root = root(coordinator)
        root.playAll(listOf(a, b)); advanceUntilIdle(); coordinator.complete(a); advanceUntilIdle()
        root.onQueueItemCompleted(a.reference); root.onQueueItemCompleted(a.reference); advanceUntilIdle()
        assertEquals(listOf(a.reference, b.reference), coordinator.requests.map { it.reference })
        coordinator.complete(b); advanceUntilIdle()
        root.onQueueItemCompleted(a.reference); root.onQueueItemCompleted(item("direct", "x").reference); advanceUntilIdle()
        assertEquals(2, coordinator.requests.size)
        root.navigateBack(); advanceUntilIdle()
        root.onQueueItemCompleted(b.reference); advanceUntilIdle()
        assertFalse(root.playbackQueue.value.isActive)
    }

    @Test
    fun unavailableFirstMiddleAndLastSettleOnlyPlayableCandidates() = runTest {
        val coordinator = ControlledCoordinator(); val a = item("direct", "a"); val b = item("youtube", "b"); val c = item("direct", "c")
        coordinator.enqueue(a, MediaOpenResult.Failure("gone", false)); coordinator.enqueue(b); coordinator.enqueue(c)
        val root = root(coordinator)
        root.playAll(listOf(a,b,c)); advanceUntilIdle(); coordinator.complete(b); advanceUntilIdle()
        assertEquals(1, root.playbackQueue.value.currentIndex); assertEquals("b", player(root).externalId)
        root.onQueueItemCompleted(b.reference); advanceUntilIdle(); coordinator.complete(c); advanceUntilIdle()
        assertEquals(2, root.playbackQueue.value.currentIndex)
    }

    @Test
    fun unavailableLastClearsQueueWithoutSettlingInvalidPlayer() = runTest {
        val coordinator = ControlledCoordinator(); val a = item("direct", "a"); val b = item("youtube", "b")
        coordinator.enqueue(a); coordinator.enqueue(b, MediaOpenResult.Failure("gone", false))
        val root = root(coordinator)
        root.playAll(listOf(a,b)); advanceUntilIdle(); coordinator.complete(a); advanceUntilIdle()
        root.onQueueItemCompleted(a.reference); advanceUntilIdle()
        assertEquals(listOf(a.reference,b.reference), coordinator.requests.map { it.reference }); assertFalse(root.playbackQueue.value.isActive); assertEquals("a", player(root).externalId)
    }

    @Test
    fun previousSkipsUnavailableCandidatesBackwardAtRootLevel() = runTest {
        val c = ControlledCoordinator(); val a=item("direct","a"); val b=item("youtube","b"); val x=item("direct","c"); val d=item("youtube","d")
        c.enqueue(a); c.enqueue(b); c.enqueue(x); c.enqueue(d); val root=root(c)
        root.playAll(listOf(a,b,x,d)); advanceUntilIdle(); c.complete(a); advanceUntilIdle()
        root.queueNext(); advanceUntilIdle(); c.complete(b); advanceUntilIdle(); root.queueNext(); advanceUntilIdle(); c.complete(x); advanceUntilIdle(); root.queueNext(); advanceUntilIdle(); c.complete(d); advanceUntilIdle()
        c.enqueue(x, MediaOpenResult.Failure("gone",false)); c.enqueue(b, MediaOpenResult.Failure("gone",false)); c.enqueue(a)
        root.queuePrevious(); advanceUntilIdle(); c.complete(a); advanceUntilIdle()
        assertEquals(listOf(x.reference,b.reference,a.reference), c.requests.takeLast(3).map { it.reference }); assertEquals(0,root.playbackQueue.value.currentIndex); assertEquals("a",player(root).externalId)
    }

    @Test
    fun previousKeepsCurrentPlayerWhenNoPlayablePredecessorExists() = runTest {
        val c=ControlledCoordinator(); val a=item("direct","a"); val b=item("youtube","b"); val x=item("direct","c"); val d=item("youtube","d")
        c.enqueue(a);c.enqueue(b);c.enqueue(x);c.enqueue(d);val root=root(c)
        root.playAll(listOf(a,b,x,d));advanceUntilIdle();c.complete(a);advanceUntilIdle();root.queueNext();advanceUntilIdle();c.complete(b);advanceUntilIdle();root.queueNext();advanceUntilIdle();c.complete(x);advanceUntilIdle();root.queueNext();advanceUntilIdle();c.complete(d);advanceUntilIdle()
        c.enqueue(x,MediaOpenResult.Failure("gone",false));c.enqueue(b,MediaOpenResult.Failure("gone",false));c.enqueue(a,MediaOpenResult.Failure("gone",false))
        root.queuePrevious();advanceUntilIdle()
        assertEquals(listOf(x.reference,b.reference,a.reference),c.requests.takeLast(3).map { it.reference });assertEquals(3,root.playbackQueue.value.currentIndex);assertNull(root.playbackQueue.value.pendingIndex);assertTrue(root.playbackQueue.value.isActive);assertEquals("d",player(root).externalId)
    }

    @Test
    fun providerCollisionAndMixedProviderQueueUseFullReferences() = runTest {
        val c=ControlledCoordinator(); val youtube=item("youtube","same"); val direct=item("direct","same"); val last=item("direct","c")
        c.enqueue(youtube); c.enqueue(direct); c.enqueue(last); val root=root(c)
        root.playAll(listOf(youtube,direct,last)); advanceUntilIdle(); c.complete(youtube); advanceUntilIdle(); root.onQueueItemCompleted(youtube.reference); advanceUntilIdle(); c.complete(direct); advanceUntilIdle(); root.onQueueItemCompleted(direct.reference); advanceUntilIdle(); c.complete(last); advanceUntilIdle()
        assertEquals(listOf(youtube.reference,direct.reference,last.reference),c.requests.map { it.reference }); assertEquals(2,root.playbackQueue.value.currentIndex)
    }

    @Test
    fun queueTransitionsReplaceCurrentPlayerAndBackReturnsToSource() = runTest {
        val c=ControlledCoordinator(); val a=item("direct","a"); val b=item("youtube","b"); val d=item("direct","d")
        c.enqueue(a);c.enqueue(b);c.enqueue(d);val root=root(c)
        root.playAll(listOf(a,b,d));advanceUntilIdle();c.complete(a);advanceUntilIdle();root.onQueueItemCompleted(a.reference);advanceUntilIdle();c.complete(b);advanceUntilIdle();root.onQueueItemCompleted(b.reference);advanceUntilIdle();c.complete(d);advanceUntilIdle();root.navigateBack();advanceUntilIdle()
        assertEquals(Configuration.Home,root.childStack.value.active.configuration)
    }

    @Test
    fun rapidNextNextPreviousSettlesLatestValidIntent() = runTest {
        val c=ControlledCoordinator();val a=item("direct","a");val b=item("youtube","b");val d=item("direct","c")
        c.enqueue(a);c.enqueue(b);c.enqueue(d);c.enqueue(b);val root=root(c)
        root.playAll(listOf(a,b,d));advanceUntilIdle();c.complete(a);advanceUntilIdle()
        root.queueNext();advanceUntilIdle();root.queueNext();advanceUntilIdle();root.queuePrevious();advanceUntilIdle()
        c.complete(d);c.complete(b);c.complete(b);advanceUntilIdle()
        assertEquals("b",player(root).externalId);assertEquals(1,root.playbackQueue.value.currentIndex);assertNull(root.playbackQueue.value.pendingIndex)
    }

    @Test
    fun navigatingBackFromPlayerClearsQueueAndReturnsToSource() = runTest {
        val coordinator = ControlledCoordinator()
        val a = item("direct", "a")
        coordinator.enqueue(a)
        val root = root(coordinator)

        root.playAll(listOf(a)); advanceUntilIdle(); coordinator.complete(a); advanceUntilIdle()
        root.navigateBack(); advanceUntilIdle()

        assertFalse(root.playbackQueue.value.isActive)
        assertEquals(Configuration.Home, root.childStack.value.active.configuration)
    }

    @Test
    fun eligibleDirectAudioBackDetachesPlayerWithoutClearingQueueOrStoppingSession() = runTest {
        val audio = item("direct", "audio")
        var detachCount = 0
        var stopCount = 0
        val root = DefaultRootComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry().also { it.onCreate() }),
            initialConfiguration = Configuration.Home,
            searchComponentFactory = { Any() },
            mediaOpenCoordinator = object : MediaOpenCoordinator {
                override suspend fun open(item: MediaCatalogItem) = MediaOpenResult.Player(
                    Configuration.Player(
                        providerId = item.reference.provider.value,
                        externalId = item.reference.externalId,
                        title = item.title,
                        playbackKind = "direct",
                        directUri = "https://example.test/audio",
                        mimeType = "audio/mpeg",
                    )
                )
            },
            coroutineContext = StandardTestDispatcher(testScheduler),
            canRetainEligibleDirectSession = { true },
            onEligiblePlayerUiDetached = { detachCount++ },
            onStopPlayback = { stopCount++ },
        )

        root.playAll(listOf(audio)); advanceUntilIdle()
        root.navigateBack(); advanceUntilIdle()

        assertEquals(1, detachCount)
        assertEquals(0, stopCount)
        assertTrue(root.playbackQueue.value.isActive)
        assertEquals(Configuration.Home, root.childStack.value.active.configuration)
    }

    @Test
    fun eligibleDirectAudioKeepsExistingSafeBackBehaviorUntilAHostReportsRetentionCapability() = runTest {
        val audio = item("direct", "audio")
        val root = DefaultRootComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry().also { it.onCreate() }),
            initialConfiguration = Configuration.Home,
            searchComponentFactory = { Any() },
            mediaOpenCoordinator = object : MediaOpenCoordinator {
                override suspend fun open(item: MediaCatalogItem) = MediaOpenResult.Player(
                    Configuration.Player(item.reference.provider.value, item.reference.externalId, item.title, playbackKind = "direct", mimeType = "audio/mpeg")
                )
            },
            coroutineContext = StandardTestDispatcher(testScheduler),
        )

        root.playAll(listOf(audio)); advanceUntilIdle()
        root.navigateBack(); advanceUntilIdle()

        assertFalse(root.playbackQueue.value.isActive)
        assertEquals(Configuration.Home, root.childStack.value.active.configuration)
    }

    @Test
    fun explicitStopInvokesSessionStopAndClearsQueue() = runTest {
        val audio = item("direct", "audio")
        var stopCount = 0
        val root = DefaultRootComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry().also { it.onCreate() }),
            initialConfiguration = Configuration.Home,
            searchComponentFactory = { Any() },
            mediaOpenCoordinator = object : MediaOpenCoordinator {
                override suspend fun open(item: MediaCatalogItem) = MediaOpenResult.Player(
                    Configuration.Player(item.reference.provider.value, item.reference.externalId, item.title, playbackKind = "direct", mimeType = "audio/mpeg")
                )
            },
            coroutineContext = StandardTestDispatcher(testScheduler),
            onStopPlayback = { stopCount++ },
        )

        root.playAll(listOf(audio)); advanceUntilIdle()
        root.stopPlayback(); advanceUntilIdle()

        assertEquals(1, stopCount)
        assertFalse(root.playbackQueue.value.isActive)
        assertEquals(Configuration.Home, root.childStack.value.active.configuration)
    }

    private fun TestScope.root(coordinator: ControlledCoordinator): DefaultRootComponent<Any> = DefaultRootComponent(
        componentContext = DefaultComponentContext(LifecycleRegistry().also { it.onCreate() }),
        initialConfiguration = Configuration.Home,
        searchComponentFactory = { Any() },
        mediaOpenCoordinator = coordinator,
        coroutineContext = StandardTestDispatcher(testScheduler)
    )

    private fun player(root: DefaultRootComponent<Any>) =
        assertIs<Configuration.Player>(root.childStack.value.active.configuration)

    private class ControlledCoordinator : MediaOpenCoordinator {
        val requests = mutableListOf<MediaCatalogItem>()
        private val responses = mutableMapOf<MediaReference, MutableList<CompletableDeferred<MediaOpenResult>>>()
        private val pending = mutableMapOf<MediaReference, MutableList<CompletableDeferred<MediaOpenResult>>>()

        fun enqueue(item: MediaCatalogItem, result: MediaOpenResult? = null) {
            responses.getOrPut(item.reference) { mutableListOf() } += CompletableDeferred<MediaOpenResult>().also {
                if (result != null) it.complete(result)
            }
        }

        fun complete(item: MediaCatalogItem) {
            pending[item.reference]?.firstOrNull { !it.isCompleted }?.complete(playerResult(item))
                ?: error("No pending result for ${item.reference}")
        }

        override suspend fun open(item: MediaCatalogItem): MediaOpenResult {
            requests += item
            val response = responses[item.reference]?.removeFirstOrNull()
                ?: error("No response enqueued for ${item.reference}")
            pending.getOrPut(item.reference) { mutableListOf() } += response
            return response.await()
        }

        private fun playerResult(item: MediaCatalogItem) = MediaOpenResult.Player(
            Configuration.Player(
                providerId = item.reference.provider.value,
                externalId = item.reference.externalId,
                title = item.title,
                playbackKind = "provider-controlled"
            )
        )
    }

    private fun item(provider: String, externalId: String = "video") = MediaCatalogItem(
        MediaReference(MediaProviderId(provider), externalId),
        "Video $externalId"
    )
}
