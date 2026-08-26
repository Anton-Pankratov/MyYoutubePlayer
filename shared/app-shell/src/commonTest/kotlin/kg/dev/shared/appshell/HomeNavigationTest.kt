package kg.dev.shared.appshell

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import kg.dev.shared.core.common.media.MediaProviders
import kg.dev.shared.core.common.media.MediaReference
import kg.dev.shared.core.ui.navigation.Configuration
import kg.dev.shared.core.ui.navigation.DefaultRootComponent
import kg.dev.shared.core.ui.navigation.MediaOpenCoordinator
import kg.dev.shared.core.ui.navigation.MediaOpenResult
import kg.dev.shared.feature.history.domain.ResumePolicy
import kg.dev.shared.feature.home.presentation.HomeMediaItemUiModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class HomeNavigationTest {
    @Test
    fun homeSelectionUsesRootMediaOpeningWithTheResolvedStartPosition() = runTest {
        val lifecycle = LifecycleRegistry().also { it.onCreate() }
        val root = DefaultRootComponent(
            componentContext = DefaultComponentContext(lifecycle),
            initialConfiguration = Configuration.Home,
            searchComponentFactory = { Any() },
            mediaOpenCoordinator = object : MediaOpenCoordinator {
                override suspend fun open(item: kg.dev.shared.core.common.media.MediaCatalogItem) = MediaOpenResult.Player(
                    Configuration.Player(
                        providerId = item.reference.provider.value,
                        externalId = item.reference.externalId,
                        title = item.title,
                        catalogDurationMs = item.durationMs,
                        playbackKind = "direct",
                        directUri = "https://example.test/video.mp4"
                    )
                )
            },
            coroutineContext = StandardTestDispatcher(testScheduler)
        )

        assertPlayerStart(root, item("unfinished", 20_000, 100_000), 20_000)
        assertPlayerStart(root, item("completed", 95_000, 100_000), 0)
        assertPlayerStart(root, item("unknown", 42_000, null), 42_000)
        lifecycle.onDestroy()
    }

    private suspend fun kotlinx.coroutines.test.TestScope.assertPlayerStart(
        root: DefaultRootComponent<Any>,
        item: HomeMediaItemUiModel,
        expectedStartPositionMs: Long
    ) {
        root.openHomeItem(item)
        advanceUntilIdle()
        val configuration = assertIs<Configuration.Player>(root.childStack.value.active.configuration)
        assertEquals(expectedStartPositionMs, configuration.startPositionMs)
        assertEquals(item.durationMs, configuration.catalogDurationMs)
    }

    private fun item(id: String, positionMs: Long, durationMs: Long?) = HomeMediaItemUiModel(
        reference = MediaReference(MediaProviders.Direct, id),
        title = id,
        thumbnailUrl = null,
        positionMs = positionMs,
        durationMs = durationMs,
        watchedAtEpochMs = 1,
        resumeDecision = ResumePolicy.evaluate(positionMs, durationMs),
        isAvailable = true
    )
}
