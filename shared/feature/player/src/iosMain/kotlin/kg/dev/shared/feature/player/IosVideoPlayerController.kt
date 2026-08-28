package kg.dev.shared.feature.player

import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.AVFoundation.*
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.CoreMedia.CMTime
import platform.Foundation.*
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_async

/** AVPlayer-backed Direct playback engine for iOS. AVFoundation stays behind [VideoPlayerController]. */
@OptIn(ExperimentalForeignApi::class)
class IosVideoPlayerController : VideoPlayerController {
    private val mutableState = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = mutableState.asStateFlow()

    /** iOS-only surface bridge. Common Player code never observes this type. */
    val avPlayer: AVPlayer? get() = player

    private var player: AVPlayer? = null
    private var item: AVPlayerItem? = null
    private var timeObserver: Any? = null
    private val notificationObservers = mutableListOf<Any>()
    private var media: PlayableMedia? = null
    private var hasStartedPlayback = false
    private var released = false

    override suspend fun play(media: PlayableMedia) {
        withContext(Dispatchers.Main) { playOnMain(media) }
    }

    private fun playOnMain(media: PlayableMedia) {
        val source = media.source as? PlaybackSource.Direct
        val url = source?.uri
            ?.takeIf(::isIosDirectUriSupported)
            ?.let { NSURL(string = it) }
        if (source == null || url == null) {
            publish(PlaybackState.Error(PlayerError.UnsupportedMedia), media = media)
            return
        }

        releaseNativeResources()
        released = false
        hasStartedPlayback = false
        this.media = media
        publish(PlaybackState.Loading, media = media)

        val newItem = AVPlayerItem(uRL = url)
        val newPlayer = AVPlayer(playerItem = newItem)
        item = newItem
        player = newPlayer
        notificationObservers += NSNotificationCenter.defaultCenter.addObserverForName(
            AVPlayerItemDidPlayToEndTimeNotification,
            newItem,
            null
        ) { onCompleted() }
        notificationObservers += NSNotificationCenter.defaultCenter.addObserverForName(
            AVPlayerItemPlaybackStalledNotification,
            newItem,
            null
        ) { if (!released) publish(PlaybackState.Buffering) }
        notificationObservers += NSNotificationCenter.defaultCenter.addObserverForName(
            AVPlayerItemNewAccessLogEntryNotification,
            newItem,
            null
        ) { if (!released) publishEngineState() }
        notificationObservers += NSNotificationCenter.defaultCenter.addObserverForName(
            AVPlayerItemNewErrorLogEntryNotification,
            newItem,
            null
        ) {
            if (!released && newItem.error != null) {
                publish(PlaybackState.Error(PlayerError.SourceUnavailable))
            }
        }
        notificationObservers += NSNotificationCenter.defaultCenter.addObserverForName(
            AVPlayerItemFailedToPlayToEndTimeNotification,
            newItem,
            null
        ) { if (!released) publish(PlaybackState.Error(PlayerError.SourceUnavailable)) }
        timeObserver = newPlayer.addPeriodicTimeObserverForInterval(
            CMTimeMakeWithSeconds(0.5, 600),
            dispatch_get_main_queue()
        ) { onTimeUpdated() }
        newPlayer.play()
    }

    override fun resume() {
        if (released) return
        runOnMain {
            player?.play()
            publishEngineState()
        }
    }

    override fun pause() {
        if (released) return
        runOnMain {
            player?.pause()
            if (hasStartedPlayback) publish(PlaybackState.Paused) else publishEngineState()
        }
    }

    override fun seekTo(positionMs: Long) {
        if (released) return
        val safePosition = positionMs.coerceAtLeast(0)
        runOnMain {
            player?.seekToTime(CMTimeMakeWithSeconds(millisecondsToSeconds(safePosition), 1_000))
            publish(positionMs = safePosition)
        }
    }

    override fun retry() {
        if (released) return
        runOnMain {
            val activePlayer = player ?: return@runOnMain
            publish(PlaybackState.Loading)
            activePlayer.play()
        }
    }

    override fun release() {
        if (released) return
        released = true
        runOnMain(::releaseNativeResources)
    }

    private fun onTimeUpdated() {
        if (!released) publishEngineState()
    }

    private fun onCompleted() {
        if (!released) publish(PlaybackState.Completed, positionMs = durationMilliseconds() ?: currentPositionMilliseconds())
    }

    private fun publishEngineState() {
        val nextState = iosDirectPlaybackState(
            itemStatus = item?.status,
            timeControlStatus = player?.timeControlStatus,
            hasStartedPlayback = hasStartedPlayback
        )
        if (nextState == PlaybackState.Playing) hasStartedPlayback = true
        publish(nextState)
    }

    private fun publish(
        playbackState: PlaybackState = mutableState.value.playbackState,
        media: PlayableMedia? = this.media,
        positionMs: Long = currentPositionMilliseconds(),
        durationMs: Long? = durationMilliseconds()
    ) {
        mutableState.value = PlayerState(
            media = media,
            playbackState = playbackState,
            positionMs = positionMs.coerceAtLeast(0),
            durationMs = durationMs,
            bufferedPositionMs = bufferedPositionMilliseconds()
        )
    }

    private fun currentPositionMilliseconds(): Long = cmtimeToMilliseconds(player?.currentTime()) ?: 0

    private fun durationMilliseconds(): Long? = cmtimeToMilliseconds(item?.duration)

    private fun bufferedPositionMilliseconds(): Long? = null

    private fun releaseNativeResources() {
        timeObserver?.let { token -> player?.removeTimeObserver(token) }
        timeObserver = null
        notificationObservers.forEach(NSNotificationCenter.defaultCenter::removeObserver)
        notificationObservers.clear()
        player?.pause()
        player?.replaceCurrentItemWithPlayerItem(null)
        player = null
        item = null
    }

    private fun runOnMain(block: () -> Unit) {
        dispatch_async(dispatch_get_main_queue()) { block() }
    }

}

@OptIn(ExperimentalForeignApi::class)
internal fun isIosDirectUriSupported(uri: String): Boolean =
    uri.startsWith("https://") || uri.startsWith("http://") || uri.startsWith("file://")

internal fun millisecondsToSeconds(milliseconds: Long): Double = milliseconds.coerceAtLeast(0) / 1_000.0

@OptIn(ExperimentalForeignApi::class)
internal fun cmtimeToMilliseconds(time: CValue<CMTime>?): Long? {
    val seconds = time?.let(::CMTimeGetSeconds) ?: return null
    return if (seconds.isFinite() && seconds >= 0) (seconds * 1_000).toLong() else null
}

@OptIn(ExperimentalForeignApi::class)
internal fun iosDirectPlaybackState(
    itemStatus: Long?,
    timeControlStatus: Long?,
    hasStartedPlayback: Boolean
): PlaybackState = when {
    itemStatus == null || timeControlStatus == null -> PlaybackState.Idle
    itemStatus == AVPlayerItemStatusFailed -> PlaybackState.Error(PlayerError.SourceUnavailable)
    itemStatus != AVPlayerItemStatusReadyToPlay -> PlaybackState.Loading
    timeControlStatus == AVPlayerTimeControlStatusPlaying -> PlaybackState.Playing
    timeControlStatus == AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate -> PlaybackState.Buffering
    hasStartedPlayback -> PlaybackState.Paused
    else -> PlaybackState.Ready
}
