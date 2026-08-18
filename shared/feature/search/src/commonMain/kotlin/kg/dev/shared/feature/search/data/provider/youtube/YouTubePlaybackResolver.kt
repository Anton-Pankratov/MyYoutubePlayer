package kg.dev.shared.feature.search.data.provider.youtube

import kg.dev.shared.core.common.media.MediaCatalogItem
import kg.dev.shared.core.common.media.MediaProviderId
import kg.dev.shared.core.common.media.MediaProviders
import kg.dev.shared.feature.player.PlayableMedia
import kg.dev.shared.feature.player.PlaybackResolution
import kg.dev.shared.feature.player.PlaybackSource
import kg.dev.shared.feature.player.PlaybackSourceResolver

/**
 * YouTube Data API supplies catalog metadata only. Playback intentionally remains provider
 * controlled until an approved official YouTube player adapter is integrated per platform.
 */
class YouTubePlaybackResolver : PlaybackSourceResolver {
    override val providerId: MediaProviderId = MediaProviders.YouTube

    override suspend fun resolve(media: MediaCatalogItem): PlaybackResolution =
        PlaybackResolution.Resolved(
            PlayableMedia(media, PlaybackSource.ProviderControlled(media.reference))
        )
}
