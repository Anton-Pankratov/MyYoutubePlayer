package kg.dev.shared.feature.player

import kg.dev.shared.core.common.media.MediaCatalogItem
import kg.dev.shared.core.ui.navigation.Configuration
import kg.dev.shared.core.ui.navigation.MediaOpenCoordinator
import kg.dev.shared.core.ui.navigation.MediaOpenResult

class DefaultMediaOpenCoordinator(
    private val registry: PlaybackSourceResolverRegistry
) : MediaOpenCoordinator {
    override suspend fun open(item: MediaCatalogItem): MediaOpenResult = when (val resolution = registry.resolve(item)) {
        is PlaybackResolution.Resolved -> playerResult(item, resolution.media.source)
        is PlaybackResolution.Failed -> when (resolution.error) {
            // Catalog providers without a native resolver still have a valid Player destination.
            // The Player presents the provider capability state without pretending direct playback exists.
            PlaybackResolutionError.ProviderNotRegistered -> playerResult(
                item,
                PlaybackSource.ProviderControlled(item.reference)
            )
            else -> MediaOpenResult.Failure(resolution.error.name.replace('_', ' '))
        }
    }

    private fun playerResult(item: MediaCatalogItem, source: PlaybackSource): MediaOpenResult.Player =
        MediaOpenResult.Player(
            Configuration.Player(
                providerId = item.reference.provider.value,
                externalId = item.reference.externalId,
                title = item.title,
                thumbnailUrl = item.thumbnailUrl,
                authorTitle = item.authorTitle,
                catalogDurationMs = item.durationMs,
                playbackKind = if (source is PlaybackSource.Direct) DIRECT else CONTROLLED,
                directUri = (source as? PlaybackSource.Direct)?.uri,
                mimeType = (source as? PlaybackSource.Direct)?.mimeType
            )
        )

    private companion object { const val DIRECT = "direct"; const val CONTROLLED = "provider-controlled" }
}
