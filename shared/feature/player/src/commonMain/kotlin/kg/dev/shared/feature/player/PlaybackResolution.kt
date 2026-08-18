package kg.dev.shared.feature.player

import kg.dev.shared.core.common.media.MediaCatalogItem
import kg.dev.shared.core.common.media.MediaProviderId
import kg.dev.shared.core.common.media.MediaReference

sealed interface PlaybackSource {
    /** A source explicitly authorized for the native platform media engine. */
    data class Direct(val uri: String, val mimeType: String? = null) : PlaybackSource
    /** The provider requires its approved SDK, embed, or player adapter. */
    data class ProviderControlled(val reference: MediaReference) : PlaybackSource
}

data class PlayableMedia(
    val catalogItem: MediaCatalogItem,
    val source: PlaybackSource
)

sealed interface PlaybackResolution {
    data class Resolved(val media: PlayableMedia) : PlaybackResolution
    data class Failed(val error: PlaybackResolutionError) : PlaybackResolution
}

enum class PlaybackResolutionError {
    ProviderNotRegistered,
    ProviderConfigurationMissing,
    ProviderAuthenticationRequired,
    PlaybackUnsupported,
    MediaUnavailable,
    PlaybackResolutionFailed,
    NetworkFailure,
    Unknown
}

interface PlaybackSourceResolver {
    val providerId: MediaProviderId
    suspend fun resolve(media: MediaCatalogItem): PlaybackResolution
}

class PlaybackSourceResolverRegistry(resolvers: Set<PlaybackSourceResolver>) {
    private val byProvider = resolvers.associateBy { it.providerId }

    init {
        require(byProvider.size == resolvers.size) { "Duplicate playback resolver registration" }
    }

    suspend fun resolve(media: MediaCatalogItem): PlaybackResolution {
        val resolver = byProvider[media.reference.provider]
            ?: return PlaybackResolution.Failed(PlaybackResolutionError.ProviderNotRegistered)
        return resolver.resolve(media)
    }
}
