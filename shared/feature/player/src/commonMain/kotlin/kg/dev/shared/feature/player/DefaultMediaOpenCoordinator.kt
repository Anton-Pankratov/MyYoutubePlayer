package kg.dev.shared.feature.player

import kg.dev.shared.core.common.media.MediaCatalogItem
import kg.dev.shared.core.ui.navigation.Configuration
import kg.dev.shared.core.ui.navigation.MediaOpenCoordinator
import kg.dev.shared.core.ui.navigation.MediaOpenResult

class DefaultMediaOpenCoordinator(
    private val registry: PlaybackSourceResolverRegistry
) : MediaOpenCoordinator {
    override suspend fun open(item: MediaCatalogItem): MediaOpenResult = when (val resolution = registry.resolve(item)) {
        is PlaybackResolution.Resolved -> {
            val source = resolution.media.source
            MediaOpenResult.Player(
                Configuration.Player(
                    providerId = item.reference.provider.value,
                    externalId = item.reference.externalId,
                    title = item.title,
                    thumbnailUrl = item.thumbnailUrl,
                    playbackKind = if (source is PlaybackSource.Direct) DIRECT else CONTROLLED,
                    directUri = (source as? PlaybackSource.Direct)?.uri,
                    mimeType = (source as? PlaybackSource.Direct)?.mimeType
                )
            )
        }
        is PlaybackResolution.Failed -> MediaOpenResult.Failure(resolution.error.name.replace('_', ' '))
    }

    private companion object { const val DIRECT = "direct"; const val CONTROLLED = "provider-controlled" }
}
