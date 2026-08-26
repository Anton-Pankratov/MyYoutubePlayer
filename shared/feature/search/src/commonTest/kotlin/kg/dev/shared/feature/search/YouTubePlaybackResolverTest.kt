package kg.dev.shared.feature.search

import kg.dev.shared.core.common.media.MediaCatalogItem
import kg.dev.shared.core.common.media.MediaProviders
import kg.dev.shared.core.common.media.MediaReference
import kg.dev.shared.feature.player.PlaybackResolution
import kg.dev.shared.feature.player.PlaybackSource
import kg.dev.shared.feature.search.data.provider.youtube.YouTubePlaybackResolver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class YouTubePlaybackResolverTest {
    @Test
    fun youtubeRemainsProviderControlledAndNeverBecomesDirectUrl() = runTest {
        val reference = MediaReference(MediaProviders.YouTube, "M7lc1UVf-VE")
        val result = assertIs<PlaybackResolution.Resolved>(
            YouTubePlaybackResolver().resolve(MediaCatalogItem(reference, "YouTube video"))
        )

        val source = assertIs<PlaybackSource.ProviderControlled>(result.media.source)
        assertEquals(reference, source.reference)
    }
}
