package kg.dev.shared.feature.player

import kotlinx.coroutines.flow.StateFlow

data class PlayerState(
    val media: PlayableMedia? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long? = null,
    val bufferedPositionMs: Long? = null,
    val isCompleted: Boolean = false,
    val error: PlayerError? = null
)

enum class PlayerError {
    UnsupportedMedia,
    NetworkFailure,
    SourceUnavailable,
    InitializationFailed,
    PlaybackFailed,
    Unknown
}

interface VideoPlayerController {
    val state: StateFlow<PlayerState>
    /** Called only for [PlaybackSource.Direct] sources. */
    suspend fun play(media: PlayableMedia)
    fun pause()
    fun seekTo(positionMs: Long)
    fun release()
}
