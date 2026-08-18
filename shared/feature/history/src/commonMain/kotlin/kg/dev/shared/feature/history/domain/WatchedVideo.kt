package kg.dev.shared.feature.history.domain

import kg.dev.shared.core.common.media.MediaReference

data class WatchedVideo(
    val reference: MediaReference,
    val title: String,
    val thumbnailUrl: String? = null,
    val positionMs: Long,
    val durationMs: Long? = null,
    val watchedAtEpochMs: Long
)
