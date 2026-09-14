package kg.dev.shared.feature.player

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionInterruptionOptionKey
import platform.AVFAudio.AVAudioSessionInterruptionOptionShouldResume
import platform.AVFAudio.AVAudioSessionInterruptionTypeBegan
import platform.AVFAudio.AVAudioSessionInterruptionTypeEnded
import platform.AVFAudio.AVAudioSessionInterruptionTypeKey
import platform.AVFAudio.AVAudioSessionMediaServicesWereResetNotification
import platform.AVFAudio.AVAudioSessionModeDefault
import platform.AVFAudio.AVAudioSessionRouteChangeNotification
import platform.AVFAudio.AVAudioSessionRouteChangeReasonKey
import platform.AVFAudio.AVAudioSessionRouteChangeReasonOldDeviceUnavailable
import platform.AVFAudio.setActive
import platform.AVFoundation.*
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.MediaPlayer.MPChangePlaybackPositionCommandEvent
import platform.MediaPlayer.MPMediaItemPropertyArtist
import platform.MediaPlayer.MPMediaItemPropertyPlaybackDuration
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPNowPlayingInfoCenter
import platform.MediaPlayer.MPNowPlayingInfoPropertyElapsedPlaybackTime
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackRate
import platform.MediaPlayer.MPRemoteCommandCenter
import platform.MediaPlayer.MPRemoteCommandHandlerStatusCommandFailed
import platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/** Process-scoped AVPlayer owner for background-eligible Direct audio. */
@OptIn(ExperimentalForeignApi::class)
class IosDirectPlaybackHost : DirectPlaybackHost {
    private val mutableState = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = mutableState.asStateFlow()
    private val mutableUiAttached = MutableStateFlow(false)
    override val isUiAttached: StateFlow<Boolean> = mutableUiAttached.asStateFlow()
    override val capabilities = DirectPlaybackHostCapabilities(true, true)

    private var player: AVPlayer? = null
    private var item: AVPlayerItem? = null
    private var media: PlayableMedia? = null
    private val sessionGeneration = IosHostSessionGeneration()
    private var started = false
    private var completionObserver: Any? = null
    private var failureObserver: Any? = null
    private var timeObserver: Any? = null
    private var remoteRouter: IosRemoteCommandRouter? = null
    private val commandCenter = MPRemoteCommandCenter.sharedCommandCenter()
    private var remoteCommandsInstalled = false
    private var interruptionObserver: Any? = null
    private var routeObserver: Any? = null
    private var mediaServicesResetObserver: Any? = null
    private val audioSessionPolicy = IosAudioSessionPolicy()

    init {
        installAudioSessionObservers()
    }

    override fun attachUi() { mutableUiAttached.value = true }
    override fun detachUi() { mutableUiAttached.value = false }

    /** Installed once by application composition; remote actions never own an iOS queue. */
    fun bindSystemCommands(callbacks: DirectPlaybackCommandCallbacks) {
        remoteRouter = IosRemoteCommandRouter(callbacks)
        if (remoteCommandsInstalled) return
        remoteCommandsInstalled = true
        commandCenter.playCommand.addTargetWithHandler {
            if (remoteRouter?.play(hasActiveMedia()) == true) MPRemoteCommandHandlerStatusSuccess else {
                MPRemoteCommandHandlerStatusCommandFailed
            }
        }
        commandCenter.pauseCommand.addTargetWithHandler {
            if (remoteRouter?.pause(hasActiveMedia()) == true) MPRemoteCommandHandlerStatusSuccess else {
                MPRemoteCommandHandlerStatusCommandFailed
            }
        }
        commandCenter.changePlaybackPositionCommand.addTargetWithHandler { event ->
            val seconds = (event as? MPChangePlaybackPositionCommandEvent)?.positionTime
            if (seconds == null || !hasActiveMedia() || remoteRouter?.seek(seconds, mutableState.value.durationMs) != true) {
                MPRemoteCommandHandlerStatusCommandFailed
            } else {
                MPRemoteCommandHandlerStatusSuccess
            }
        }
        commandCenter.nextTrackCommand.addTargetWithHandler {
            if (remoteRouter?.next(hasActiveMedia()) == true) MPRemoteCommandHandlerStatusSuccess else {
                MPRemoteCommandHandlerStatusCommandFailed
            }
        }
        commandCenter.previousTrackCommand.addTargetWithHandler {
            if (remoteRouter?.previous(hasActiveMedia()) == true) MPRemoteCommandHandlerStatusSuccess else {
                MPRemoteCommandHandlerStatusCommandFailed
            }
        }
        commandCenter.stopCommand.addTargetWithHandler {
            if (remoteRouter?.stop(hasActiveMedia()) == true) MPRemoteCommandHandlerStatusSuccess else {
                MPRemoteCommandHandlerStatusCommandFailed
            }
        }
    }

    override suspend fun play(media: PlayableMedia) {
        withContext(Dispatchers.Main) {
            val source = media.source as? PlaybackSource.Direct
            val url = source?.uri?.takeIf(::isIosDirectUriSupported)?.let { NSURL(string = it) }
            if (url == null) {
                publish(PlaybackState.Error(PlayerError.UnsupportedMedia), media)
                return@withContext
            }

            // Invalidate first. A queued callback for the old item cannot mutate this session.
            val generation = sessionGeneration.replaceOrInvalidate()
            removeItemObservers()
            audioSessionPolicy.onSessionStarted(generation)
            this@IosDirectPlaybackHost.media = media
            started = false

            val newItem = AVPlayerItem(uRL = url)
            item = newItem
            val activePlayer = player ?: AVPlayer(playerItem = newItem).also { player = it }
            if (activePlayer.currentItem !== newItem) activePlayer.replaceCurrentItemWithPlayerItem(newItem)
            installItemObservers(newItem, generation, media)

            if (!activateAudioSession()) {
                audioSessionPolicy.onPause(generation)
                activePlayer.pause()
                publish(PlaybackState.Error(PlayerError.SourceUnavailable), media)
                return@withContext
            }
            publish(PlaybackState.Loading, media)
            activePlayer.play()
            updateNowPlaying()
        }
    }

    override fun play() = onMain {
        val generation = sessionGeneration.current()
        if (!hasActiveMedia() || !activateAudioSession()) {
            if (hasActiveMedia()) {
                audioSessionPolicy.onPause(generation)
                publish(PlaybackState.Error(PlayerError.SourceUnavailable))
            }
            return@onMain
        }
        audioSessionPolicy.onPlay(generation)
        player?.play()
        publishEngineState()
    }

    override fun pause() = onMain {
        val generation = sessionGeneration.current()
        if (!hasActiveMedia()) return@onMain
        audioSessionPolicy.onPause(generation)
        player?.pause()
        publish(PlaybackState.Paused)
    }

    override fun seekTo(positionMs: Long) = onMain {
        if (!hasActiveMedia()) return@onMain
        val clamped = mutableState.value.durationMs?.takeIf { it > 0 }
            ?.let { positionMs.coerceIn(0, it) }
            ?: positionMs.coerceAtLeast(0)
        player?.seekToTime(CMTimeMakeWithSeconds(millisecondsToSeconds(clamped), 1_000))
        publish(positionMs = clamped)
    }

    override fun stop() = onMain {
        val stoppedGeneration = sessionGeneration.current()
        val invalidatedGeneration = sessionGeneration.replaceOrInvalidate()
        audioSessionPolicy.onStop(stoppedGeneration)
        removeItemObservers()
        player?.pause()
        player?.replaceCurrentItemWithPlayerItem(null)
        item = null
        media = null
        started = false
        deactivateAudioSession()
        mutableState.value = PlayerState(sessionGeneration = invalidatedGeneration)
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = null
    }

    private fun installItemObservers(newItem: AVPlayerItem, generation: Long, media: PlayableMedia) {
        completionObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            AVPlayerItemDidPlayToEndTimeNotification,
            newItem,
            null,
        ) {
            onMain {
                if (isCurrentItem(generation, newItem) && sessionGeneration.claimCompletion(generation)) {
                    publish(PlaybackState.Completed, media)
                }
            }
        }
        failureObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            AVPlayerItemFailedToPlayToEndTimeNotification,
            newItem,
            null,
        ) {
            onMain {
                if (isCurrentItem(generation, newItem)) {
                    audioSessionPolicy.onPause(generation)
                    player?.pause()
                    publish(iosCurrentItemFailureState(), media)
                }
            }
        }
        timeObserver = player?.addPeriodicTimeObserverForInterval(
            CMTimeMakeWithSeconds(1.0, 1_000),
            dispatch_get_main_queue(),
        ) { time ->
            if (isCurrentItem(generation, newItem)) {
                publishEngineState(cmtimeToMilliseconds(time) ?: 0)
            }
        }
    }

    private fun publishEngineState(positionMs: Long = cmtimeToMilliseconds(player?.currentTime()) ?: 0) {
        val next = when {
            item?.status == AVPlayerItemStatusFailed -> iosCurrentItemFailureState()
            item?.status != AVPlayerItemStatusReadyToPlay -> PlaybackState.Loading
            player?.timeControlStatus == AVPlayerTimeControlStatusPlaying -> PlaybackState.Playing
            player?.timeControlStatus == AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate -> PlaybackState.Buffering
            started -> PlaybackState.Paused
            else -> PlaybackState.Ready
        }
        if (next == PlaybackState.Playing) started = true
        if (next is PlaybackState.Error) audioSessionPolicy.onPause(sessionGeneration.current())
        publish(next, positionMs = positionMs)
    }

    private fun publish(
        playbackState: PlaybackState = mutableState.value.playbackState,
        currentMedia: PlayableMedia? = media,
        positionMs: Long = cmtimeToMilliseconds(player?.currentTime()) ?: 0,
    ) {
        mutableState.value = PlayerState(
            media = currentMedia,
            playbackState = playbackState,
            positionMs = positionMs,
            durationMs = cmtimeToMilliseconds(item?.duration),
            sessionGeneration = sessionGeneration.current(),
        )
        updateNowPlaying()
    }

    private fun hasActiveMedia(): Boolean = item != null && media != null

    private fun isCurrentItem(generation: Long, expectedItem: AVPlayerItem): Boolean =
        sessionGeneration.isCurrent(generation) && item === expectedItem && hasActiveMedia()

    private fun onMain(block: () -> Unit) = dispatch_async(dispatch_get_main_queue(), block)

    private fun removeItemObservers() {
        completionObserver?.let(NSNotificationCenter.defaultCenter::removeObserver)
        completionObserver = null
        failureObserver?.let(NSNotificationCenter.defaultCenter::removeObserver)
        failureObserver = null
        timeObserver?.let { observer -> player?.removeTimeObserver(observer) }
        timeObserver = null
    }

    private fun activateAudioSession(): Boolean {
        val audioSession = AVAudioSession.sharedInstance()
        return audioSession.setCategory(AVAudioSessionCategoryPlayback, error = null) &&
            audioSession.setMode(AVAudioSessionModeDefault, error = null) &&
            audioSession.setActive(true, withOptions = 0u, error = null)
    }

    private fun deactivateAudioSession() {
        AVAudioSession.sharedInstance().setActive(false, withOptions = 0u, error = null)
    }

    private fun installAudioSessionObservers() {
        if (interruptionObserver != null || routeObserver != null || mediaServicesResetObserver != null) return
        val center = NSNotificationCenter.defaultCenter()
        interruptionObserver = center.addObserverForName(AVAudioSessionInterruptionNotification, null, null) { notification ->
            onMain {
                val type = (notification?.userInfo?.get(AVAudioSessionInterruptionTypeKey) as? NSNumber)?.unsignedLongValue
                val generation = sessionGeneration.current()
                if (type == AVAudioSessionInterruptionTypeBegan) {
                    if (audioSessionPolicy.onInterruptionBegan(
                            generation,
                            wasPlaying = hasActiveMedia() && mutableState.value.playbackState == PlaybackState.Playing,
                        )
                    ) {
                        player?.pause()
                        publish(PlaybackState.Paused)
                    }
                } else if (type == AVAudioSessionInterruptionTypeEnded) {
                    val options = (notification?.userInfo?.get(AVAudioSessionInterruptionOptionKey) as? NSNumber)
                        ?.unsignedLongValue ?: 0uL
                    val shouldResume = options and AVAudioSessionInterruptionOptionShouldResume != 0uL
                    // claimResume revalidates current intent and atomically consumes this end event.
                    if (audioSessionPolicy.claimResume(generation, shouldResume) && isCurrentItem(generation, item ?: return@onMain)) {
                        if (activateAudioSession()) {
                            player?.play()
                            publishEngineState()
                        } else {
                            audioSessionPolicy.onPause(generation)
                            publish(PlaybackState.Error(PlayerError.SourceUnavailable))
                        }
                    }
                }
            }
        }
        routeObserver = center.addObserverForName(AVAudioSessionRouteChangeNotification, null, null) { notification ->
            onMain {
                val reason = (notification?.userInfo?.get(AVAudioSessionRouteChangeReasonKey) as? NSNumber)?.unsignedLongValue
                if (iosRouteChangeAction(reason == AVAudioSessionRouteChangeReasonOldDeviceUnavailable) == IosRouteChangeAction.PauseAndCancelResume) {
                    val generation = sessionGeneration.current()
                    if (audioSessionPolicy.onRouteLost(generation) && hasActiveMedia()) {
                        player?.pause()
                        publish(PlaybackState.Paused)
                    }
                }
            }
        }
        mediaServicesResetObserver = center.addObserverForName(
            AVAudioSessionMediaServicesWereResetNotification,
            null,
            null,
        ) {
            onMain {
                val generation = sessionGeneration.current()
                if (hasActiveMedia()) {
                    audioSessionPolicy.onMediaServicesReset(generation)
                    player?.pause()
                    publish(PlaybackState.Paused)
                }
            }
        }
    }

    private fun updateNowPlaying() {
        val activeMedia = media ?: return
        val projection = nowPlayingProjection(activeMedia, mutableState.value)
        val info = buildMap<Any?, Any> {
            put(MPMediaItemPropertyTitle, projection.title)
            projection.author?.let { put(MPMediaItemPropertyArtist, it) }
            projection.durationSeconds?.let { put(MPMediaItemPropertyPlaybackDuration, it) }
            put(MPNowPlayingInfoPropertyElapsedPlaybackTime, projection.elapsedSeconds)
            put(MPNowPlayingInfoPropertyPlaybackRate, projection.playbackRate)
        }
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = info
    }
}
