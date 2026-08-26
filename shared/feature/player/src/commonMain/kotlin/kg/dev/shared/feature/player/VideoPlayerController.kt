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
    /** Loads a new [PlaybackSource.Direct] session and starts it. */
    suspend fun play(media: PlayableMedia)
    /** Continues the already loaded session without replacing its media item. */
    fun resume()
    fun pause()
    fun seekTo(positionMs: Long)
    /** Retries the current session while preserving its current position when possible. */
    fun retry()
    fun release()
}
