package kg.dev.shared.feature.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** iOS has no native Direct playback engine yet; ProviderControlled sessions do not use this. */
class IosUnavailableVideoPlayerController : VideoPlayerController {
    private val mutableState = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = mutableState.asStateFlow()

    override suspend fun play(media: PlayableMedia) = unavailable(media)
    override fun resume() = unavailable(mutableState.value.media)
    override fun pause() = Unit
    override fun seekTo(positionMs: Long) = Unit
    override fun retry() = unavailable(mutableState.value.media)
    override fun release() = Unit

    private fun unavailable(media: PlayableMedia?) {
        mutableState.value = PlayerState(
            media = media,
            playbackState = PlaybackState.Error(PlayerError.UnsupportedMedia),
            positionMs = mutableState.value.positionMs,
            durationMs = mutableState.value.durationMs
        )
    }
}
