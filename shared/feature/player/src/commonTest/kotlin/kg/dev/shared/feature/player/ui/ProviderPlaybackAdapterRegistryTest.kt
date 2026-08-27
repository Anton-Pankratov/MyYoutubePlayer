package kg.dev.shared.feature.player.ui

import kg.dev.shared.core.common.media.MediaProviders
import kg.dev.shared.feature.player.ProviderPlaybackAdapterRegistry
import kg.dev.shared.feature.player.RenderOnlyProviderPlaybackAdapter
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class ProviderPlaybackAdapterRegistryTest {
    @Test
    fun registeredAdapterIsResolvedForInternalProviderSurface() {
        val adapter = RenderOnlyProviderPlaybackAdapter(MediaProviders.YouTube) { _, _, _ -> }
        val registry = ProviderPlaybackAdapterRegistry(listOf(adapter))

        assertSame(adapter, registry[MediaProviders.YouTube])
        assertNull(registry[MediaProviders.Direct])
    }

    @Test
    fun duplicateProviderAdaptersAreRejected() {
        val first = RenderOnlyProviderPlaybackAdapter(MediaProviders.YouTube) { _, _, _ -> }
        val second = RenderOnlyProviderPlaybackAdapter(MediaProviders.YouTube) { _, _, _ -> }

        assertFailsWith<IllegalArgumentException> {
            ProviderPlaybackAdapterRegistry(listOf(first, second))
        }
    }
}
