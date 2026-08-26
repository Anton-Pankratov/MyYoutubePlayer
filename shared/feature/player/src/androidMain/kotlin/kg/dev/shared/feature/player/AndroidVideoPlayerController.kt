package kg.dev.shared.feature.player

import android.content.Context
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AndroidVideoPlayerController(
    context: Context
) : VideoPlayerController {
    private val player = ExoPlayer.Builder(context.applicationContext).build()
    /** Android-only surface bridge. The common controller contract never exposes this type. */
    val media3Player: Player get() = player
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = mutableState.asStateFlow()
    private var progressJob: Job? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                publishState()
                if (isPlaying) {
                    startProgressUpdates()
                } else {
                    progressJob?.cancel()
                    progressJob = null
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) = publishState()

            override fun onPlayerError(error: PlaybackException) {
                mutableState.value = mutableState.value.copy(error = error.toPlayerError())
            }
        })
    }

    override suspend fun play(media: PlayableMedia) {
        val source = media.source as? PlaybackSource.Direct
        if (source == null || source.uri.isBlank()) {
            mutableState.value = PlayerState(media = media, error = PlayerError.UnsupportedMedia)
            return
        }
        mutableState.value = PlayerState(media = media)
        player.setMediaItem(
            androidx.media3.common.MediaItem.Builder().setUri(source.uri).setMimeType(source.mimeType).build()
        )
        player.prepare()
        player.play()
    }

    override fun resume() {
        player.play()
    }

    override fun pause() {
        player.pause()
    }

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0))
        publishState()
    }

    override fun retry() {
        player.prepare()
        player.play()
    }

    override fun release() {
        progressJob?.cancel()
        player.release()
        scope.cancel()
    }

    private fun startProgressUpdates() {
        if (progressJob?.isActive == true) return
        progressJob = scope.launch {
            while (isActive) {
                publishState()
                delay(PROGRESS_INTERVAL_MS)
            }
        }
    }

    private fun publishState() {
        val duration = player.duration.takeIf { it >= 0 }
        mutableState.value = mutableState.value.copy(
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = duration,
            bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0),
            isCompleted = player.playbackState == Player.STATE_ENDED,
            error = null
        )
    }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 5_000L
    }
}

private fun PlaybackException.toPlayerError(): PlayerError = when (errorCode) {
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> PlayerError.NetworkFailure
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
    PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> PlayerError.SourceUnavailable
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> PlayerError.UnsupportedMedia
    else -> PlayerError.PlaybackFailed
}
