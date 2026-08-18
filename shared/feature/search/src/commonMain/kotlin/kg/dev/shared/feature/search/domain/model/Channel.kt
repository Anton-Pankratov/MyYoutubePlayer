package kg.dev.shared.feature.search.domain.model

import kg.dev.shared.core.common.media.MediaProviderId
import kg.dev.shared.core.common.media.MediaProviders

data class Channel(
    val id: String,
    val title: String,
    val description: String,
    val thumbnailUrl: String?,
    val providerId: MediaProviderId = MediaProviders.YouTube
)
