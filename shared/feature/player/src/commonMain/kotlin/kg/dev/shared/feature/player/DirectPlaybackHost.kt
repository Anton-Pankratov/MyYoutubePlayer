package kg.dev.shared.feature.player

import kg.dev.shared.core.common.media.DirectBackgroundEligibility
import kg.dev.shared.core.common.media.directMimeBackgroundEligibility
import kotlinx.coroutines.flow.StateFlow

/** Process-lifetime Direct-playback seam for future platform background hosts. */
interface DirectPlaybackHost {
    val state: StateFlow<PlayerState>
    val isUiAttached: StateFlow<Boolean>
    val capabilities: DirectPlaybackHostCapabilities

    fun attachUi()
    fun detachUi()
    suspend fun play(media: PlayableMedia)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun stop()
}

data class DirectPlaybackHostCapabilities(
    val supportsBackgroundPlayback: Boolean = false,
    val supportsSystemMediaControls: Boolean = false,
)

fun PlayableMedia.directBackgroundEligibility(): DirectBackgroundEligibility {
    val mimeType = (source as? PlaybackSource.Direct)?.mimeType
    return directMimeBackgroundEligibility(mimeType)
}

/** Platform system-media surfaces call these outward commands; they never own a queue. */
interface DirectPlaybackCommandCallbacks {
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun next()
    fun previous()
    fun stop()
}

/** Routes platform system-media commands without introducing a host-local playlist. */
class DirectPlaybackCommandRouter(
    private val host: DirectPlaybackHost,
    private val callbacks: DirectPlaybackCommandCallbacks,
) {
    fun play() = host.play()
    fun pause() = host.pause()
    fun seekTo(positionMs: Long) = host.seekTo(positionMs)
    fun next() = callbacks.next()
    fun previous() = callbacks.previous()
    fun stop() = callbacks.stop()
}
