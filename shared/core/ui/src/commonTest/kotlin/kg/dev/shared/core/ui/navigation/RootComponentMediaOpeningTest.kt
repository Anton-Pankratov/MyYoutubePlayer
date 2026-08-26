package kg.dev.shared.core.ui.navigation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import kg.dev.shared.core.common.media.MediaCatalogItem
import kg.dev.shared.core.common.media.MediaProviderId
import kg.dev.shared.core.common.media.MediaReference
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

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

    private fun item(provider: String) = MediaCatalogItem(
        MediaReference(MediaProviderId(provider), "video"),
        "Video"
    )
}
