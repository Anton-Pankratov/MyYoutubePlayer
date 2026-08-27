package kg.dev.shared.feature.player

import kotlinx.coroutines.flow.StateFlow

/** Provider- and platform-neutral lifecycle for one playback session. */
sealed interface PlaybackState {
    data object Idle : PlaybackState
    data object Loading : PlaybackState
    data object Ready : PlaybackState
    data object Playing : PlaybackState
    data object Paused : PlaybackState
    data object Buffering : PlaybackState
    data object Completed : PlaybackState
    data class Error(val error: PlayerError) : PlaybackState
}

data class PlayerState(
    val media: PlayableMedia? = null,
    val playbackState: PlaybackState = PlaybackState.Idle,
    val positionMs: Long = 0,
    val durationMs: Long? = null,
    val bufferedPositionMs: Long? = null
) {
    val isPlaying: Boolean get() = playbackState == PlaybackState.Playing
    val isCompleted: Boolean get() = playbackState == PlaybackState.Completed
    val error: PlayerError? get() = (playbackState as? PlaybackState.Error)?.error
}

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
