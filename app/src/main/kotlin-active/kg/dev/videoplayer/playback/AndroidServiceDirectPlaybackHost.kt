package kg.dev.videoplayer.playback

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kg.dev.shared.feature.player.DirectPlaybackHost
import kg.dev.shared.feature.player.DirectPlaybackHostCapabilities
import kg.dev.shared.feature.player.PlayableMedia
import kg.dev.shared.feature.player.PlaybackSource
import kg.dev.shared.feature.player.PlaybackState
import kg.dev.shared.feature.player.PlayerError
import kg.dev.shared.feature.player.PlayerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** MediaController proxy for the service-owned Direct-audio ExoPlayer. */
@androidx.annotation.OptIn(UnstableApi::class)
class AndroidServiceDirectPlaybackHost(
    private val context: Context,
) : DirectPlaybackHost {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = mutableState.asStateFlow()
    private val mutableUiAttached = MutableStateFlow(false)
    override val isUiAttached: StateFlow<Boolean> = mutableUiAttached.asStateFlow()
    override val capabilities = DirectPlaybackHostCapabilities(
        supportsBackgroundPlayback = true,
        supportsSystemMediaControls = true,
    )
    private var media: PlayableMedia? = null
    private var controller: MediaController? = null
    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
    private var started = false
    private var error: PlayerError? = null
    private var sessionGeneration = 0L
    private var progressJob: Job? = null

    override fun attachUi() { mutableUiAttached.value = true }
    override fun detachUi() { mutableUiAttached.value = false }

    override suspend fun play(media: PlayableMedia) {
        val source = media.source as? PlaybackSource.Direct
        if (source == null || source.uri.isBlank()) {
            mutableState.value = PlayerState(media, PlaybackState.Error(PlayerError.UnsupportedMedia))
            return
        }
        this.media = media
        sessionGeneration++
        error = null
        mutableState.value = PlayerState(media, PlaybackState.Loading, sessionGeneration = sessionGeneration)
        val player = connect()
        player.setMediaItem(
            MediaItem.Builder()
                .setMediaId("${media.catalogItem.reference.provider.value}:${media.catalogItem.reference.externalId}")
                .setUri(source.uri)
                .setMimeType(source.mimeType)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(media.catalogItem.title)
                        .setArtist(media.catalogItem.authorTitle)
                        .build()
                )
                .build()
        )
        player.prepare()
        player.play()
    }

    override fun play() { controller?.play() }
    override fun pause() { controller?.pause(); publishState() }
    override fun seekTo(positionMs: Long) { controller?.seekTo(positionMs.coerceAtLeast(0)); publishState() }

    override fun stop() {
        sessionGeneration++
        progressJob?.cancel()
        progressJob = null
        controller?.stop()
        media = null
        started = false
        error = null
        mutableState.value = PlayerState(sessionGeneration = sessionGeneration)
        appContext.stopService(Intent(appContext, AndroidDirectAudioPlaybackService::class.java))
    }

    private suspend fun connect(): MediaController {
        controller?.let { return it }
        ContextCompat.startForegroundService(appContext, Intent(appContext, AndroidDirectAudioPlaybackService::class.java))
        val future = controllerFuture ?: MediaController.Builder(
            appContext,
            SessionToken(appContext, ComponentName(appContext, AndroidDirectAudioPlaybackService::class.java)),
        ).buildAsync().also { controllerFuture = it }
        return withContext(Dispatchers.IO) { future.get() }.also { connected ->
            controller = connected
            connected.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    publishState()
                    if (isPlaying) startProgressUpdates() else {
                        progressJob?.cancel()
                        progressJob = null
                    }
                }
                override fun onPlaybackStateChanged(playbackState: Int) = publishState()
                override fun onPlayerError(error: PlaybackException) {
                    this@AndroidServiceDirectPlaybackHost.error = error.toPlayerError()
                    publishState()
                }
            })
            publishState()
        }
    }

    private fun publishState() {
        val player = controller ?: return
        val duration = player.duration.takeIf { it >= 0 }
        val playbackState = error?.let(PlaybackState::Error) ?: when (player.playbackState) {
            Player.STATE_BUFFERING -> PlaybackState.Buffering
            Player.STATE_READY -> if (player.isPlaying) { started = true; PlaybackState.Playing }
            else if (started) PlaybackState.Paused else PlaybackState.Ready
            Player.STATE_ENDED -> PlaybackState.Completed
            Player.STATE_IDLE -> if (media == null) PlaybackState.Idle else PlaybackState.Loading
            else -> PlaybackState.Loading
        }
        mutableState.value = PlayerState(
            media,
            playbackState,
            player.currentPosition.coerceAtLeast(0),
            duration,
            player.bufferedPosition.coerceAtLeast(0),
            sessionGeneration,
        )
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
