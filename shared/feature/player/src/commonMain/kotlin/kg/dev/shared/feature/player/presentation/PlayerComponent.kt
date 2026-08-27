package kg.dev.shared.feature.player.presentation

import kg.dev.shared.feature.player.PlayableMedia
import kg.dev.shared.feature.player.ProviderPlaybackSession
import kg.dev.shared.feature.player.PlaybackState
import kg.dev.shared.feature.player.PlayerError
import kotlinx.coroutines.flow.StateFlow

data class PlayerUiState(
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

/** Shared product owner for playback state and durable watch progress. */
interface PlayerComponent {
    val state: StateFlow<PlayerUiState>
    val providerPlaybackSession: ProviderPlaybackSession?
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun retry()
}
