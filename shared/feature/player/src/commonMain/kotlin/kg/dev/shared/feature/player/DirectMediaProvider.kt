package kg.dev.shared.feature.player

import kg.dev.shared.core.common.media.MediaCatalogItem
import kg.dev.shared.core.common.media.MediaProviderId
import kg.dev.shared.core.common.media.MediaProviders
import kg.dev.shared.core.common.media.MediaReference

/** Application-owned catalog descriptor. It never accepts a webpage for extraction. */
data class DirectMediaDescriptor(
    val id: String,
    val title: String,
    val uri: String,
    val mimeType: String? = null,
    val thumbnailUrl: String? = null,
    val authorTitle: String? = null,
    val durationMs: Long? = null
)

class DirectMediaProvider(private val store: DirectMediaSourceStore) : PlaybackSourceResolver {
    override val providerId: MediaProviderId = MediaProviders.Direct
    /** Only durable, application-authorized direct URIs may be registered here. */
    suspend fun register(descriptor: DirectMediaDescriptor) = store.upsert(descriptor.toStored())

    override suspend fun resolve(media: MediaCatalogItem): PlaybackResolution {
        val descriptor = store.find(media.reference.externalId)
            ?: return PlaybackResolution.Failed(PlaybackResolutionError.MediaUnavailable)
        return PlaybackResolution.Resolved(
            PlayableMedia(descriptor.toCatalogItem(), PlaybackSource.Direct(descriptor.uri, descriptor.mimeType))
        )
    }

    private fun StoredDirectMedia.toCatalogItem() = MediaCatalogItem(
        reference = MediaReference(MediaProviders.Direct, externalId), title = title,
        thumbnailUrl = thumbnailUrl, authorTitle = authorTitle, durationMs = durationMs
    )

    private fun DirectMediaDescriptor.toStored() = StoredDirectMedia(id, title, uri, mimeType, thumbnailUrl, authorTitle, durationMs)
}
