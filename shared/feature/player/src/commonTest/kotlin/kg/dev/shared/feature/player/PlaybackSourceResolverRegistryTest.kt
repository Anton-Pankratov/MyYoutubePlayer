package kg.dev.shared.feature.player

import kg.dev.shared.core.common.media.MediaCatalogItem
import kg.dev.shared.core.common.media.MediaProviderId
import kg.dev.shared.core.common.media.MediaProviders
import kg.dev.shared.core.common.media.MediaReference
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class PlaybackSourceResolverRegistryTest {
    @Test fun selectsYouTubeResolver() = runTest {
        val registry = PlaybackSourceResolverRegistry(setOf(FakeResolver(MediaProviders.YouTube)))
        assertIs<PlaybackResolution.Resolved>(registry.resolve(item(MediaProviders.YouTube)))
    }

    @Test fun selectsDirectResolver() = runTest {
        val registry = PlaybackSourceResolverRegistry(setOf(FakeResolver(MediaProviders.Direct)))
        assertIs<PlaybackResolution.Resolved>(registry.resolve(item(MediaProviders.Direct)))
    }

    @Test fun unknownProviderFailsExplicitly() = runTest {
        val result = PlaybackSourceResolverRegistry(emptySet()).resolve(item(MediaProviderId("future")))
        assertEquals(PlaybackResolutionError.ProviderNotRegistered, (result as PlaybackResolution.Failed).error)
    }

    @Test fun duplicateRegistrationFailsPredictably() {
        assertFailsWith<IllegalArgumentException> {
            PlaybackSourceResolverRegistry(setOf(FakeResolver(MediaProviders.Direct), FakeResolver(MediaProviders.Direct)))
        }
    }

    private fun item(provider: MediaProviderId) = MediaCatalogItem(MediaReference(provider, "id"), "item")
    private class FakeResolver(override val providerId: MediaProviderId) : PlaybackSourceResolver {
        override suspend fun resolve(media: MediaCatalogItem) = PlaybackResolution.Resolved(
            PlayableMedia(media, PlaybackSource.ProviderControlled(media.reference))
        )
    }
}
