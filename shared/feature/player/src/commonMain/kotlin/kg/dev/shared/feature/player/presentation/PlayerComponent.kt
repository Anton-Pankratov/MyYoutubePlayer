package kg.dev.shared.feature.player.presentation

import kg.dev.shared.feature.player.PlayableMedia
import kg.dev.shared.feature.player.PlayerError
import kotlinx.coroutines.flow.StateFlow

data class PlayerUiState(
    val media: PlayableMedia? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long? = null,
    val bufferedPositionMs: Long? = null,
    val error: PlayerError? = null,
    val isCompleted: Boolean = false
)

/** Shared product owner for playback state and durable watch progress. */
interface PlayerComponent {
    val state: StateFlow<PlayerUiState>
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun retry()
}
