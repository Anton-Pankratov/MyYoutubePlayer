package kg.dev.shared.feature.player

import kg.dev.shared.core.common.media.MediaCatalogItem
import kg.dev.shared.core.common.media.MediaProviderId
import kg.dev.shared.core.common.media.MediaReference
import kg.dev.shared.core.common.media.MediaProviders
import kg.dev.shared.core.ui.navigation.MediaOpenResult
import kg.dev.shared.feature.player.library.SavedMedia
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class DefaultMediaOpenCoordinatorTest {
    @Test
    fun unregisteredCatalogProviderOpensProviderControlledPlayer() = runTest {
        val coordinator = DefaultMediaOpenCoordinator(PlaybackSourceResolverRegistry(emptySet()))

        val result = assertIs<MediaOpenResult.Player>(coordinator.open(catalogItem()))

        assertEquals("youtube", result.configuration.providerId)
        assertEquals("video-id", result.configuration.externalId)
        assertEquals("provider-controlled", result.configuration.playbackKind)
        assertNull(result.configuration.directUri)
    }

    @Test
    fun registeredProviderFailureStillReturnsFailure() = runTest {
        val resolver = object : PlaybackSourceResolver {
            override val providerId = MediaProviderId("youtube")
            override suspend fun resolve(media: MediaCatalogItem) =
                PlaybackResolution.Failed(PlaybackResolutionError.MediaUnavailable)
        }
        val coordinator = DefaultMediaOpenCoordinator(PlaybackSourceResolverRegistry(setOf(resolver)))

        assertIs<MediaOpenResult.Failure>(coordinator.open(catalogItem()))
    }

    @Test
    fun directResolutionCreatesInternalDirectPlayerConfiguration() = runTest {
        val resolver = object : PlaybackSourceResolver {
            override val providerId = MediaProviderId("direct")
            override suspend fun resolve(media: MediaCatalogItem) = PlaybackResolution.Resolved(
                PlayableMedia(media, PlaybackSource.Direct("https://media.example/video.mp4", "video/mp4"))
            )
        }
        val coordinator = DefaultMediaOpenCoordinator(PlaybackSourceResolverRegistry(setOf(resolver)))

        val result = assertIs<MediaOpenResult.Player>(
            coordinator.open(
                MediaCatalogItem(MediaReference(MediaProviderId("direct"), "direct-video"), "Direct")
            )
        )

        assertEquals("direct", result.configuration.playbackKind)
        assertEquals("https://media.example/video.mp4", result.configuration.directUri)
        assertEquals("video/mp4", result.configuration.mimeType)
    }

    @Test
    fun savedYoutubeMediaUsesProviderControlledOpenPath() = runTest {
        val saved = SavedMedia(MediaReference(MediaProviders.YouTube, "saved-youtube"), "Saved", null, null, null, true, false, 1, null)
        val result = assertIs<MediaOpenResult.Player>(DefaultMediaOpenCoordinator(PlaybackSourceResolverRegistry(emptySet())).open(saved.toCatalogItem()))
        assertEquals("provider-controlled", result.configuration.playbackKind)
        assertNull(result.configuration.directUri)
        assertEquals("saved-youtube", result.configuration.externalId)
    }

    private fun catalogItem() = MediaCatalogItem(
        reference = MediaReference(MediaProviderId("youtube"), "video-id"),
        title = "A video",
        thumbnailUrl = "https://example.test/thumb.jpg"
    )
}
