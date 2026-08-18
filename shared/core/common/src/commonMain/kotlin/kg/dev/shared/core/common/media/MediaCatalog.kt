package kg.dev.shared.core.common.media

/** Extensible identity namespace; providers are not a closed set. */
data class MediaProviderId(val value: String)

object MediaProviders {
    val YouTube = MediaProviderId("youtube")
    val Direct = MediaProviderId("direct")
}

/** Durable content identity. A playback URL, especially a signed URL, is never this identity. */
data class MediaReference(
    val provider: MediaProviderId,
    val externalId: String
)

/** Provider-neutral catalog metadata; catalog availability does not imply native playback. */
data class MediaCatalogItem(
    val reference: MediaReference,
    val title: String,
    val thumbnailUrl: String? = null,
    val authorTitle: String? = null,
    val durationMs: Long? = null
)
