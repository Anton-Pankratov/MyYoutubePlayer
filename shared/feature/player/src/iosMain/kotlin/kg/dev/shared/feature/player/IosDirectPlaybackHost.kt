package kg.dev.shared.feature.player

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import platform.AVFoundation.*
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionInterruptionOptionKey
import platform.AVFAudio.AVAudioSessionInterruptionOptionShouldResume
import platform.AVFAudio.AVAudioSessionInterruptionTypeBegan
import platform.AVFAudio.AVAudioSessionInterruptionTypeKey
import platform.AVFAudio.AVAudioSessionRouteChangeNotification
import platform.AVFAudio.AVAudioSessionRouteChangeReasonKey
import platform.AVFAudio.AVAudioSessionRouteChangeReasonOldDeviceUnavailable
import platform.AVFAudio.AVAudioSessionModeDefault
import platform.AVFAudio.setActive
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.darwin.NSObject
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.MediaPlayer.MPChangePlaybackPositionCommandEvent
import platform.MediaPlayer.MPMediaItemPropertyArtist
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPNowPlayingInfoCenter
import platform.MediaPlayer.MPNowPlayingInfoPropertyElapsedPlaybackTime
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackRate
import platform.MediaPlayer.MPMediaItemPropertyPlaybackDuration
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
    private var generation = 0L
    private var started = false
    private var completionObserver: Any? = null
    private var timeObserver: Any? = null
    private var remoteRouter: IosRemoteCommandRouter? = null
    private val commandCenter = MPRemoteCommandCenter.sharedCommandCenter()
    private var remoteCommandsInstalled = false
    private var interruptionObserver: Any? = null
    private var routeObserver: Any? = null
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
            if (remoteRouter?.play(item != null) == true) MPRemoteCommandHandlerStatusSuccess else {
                MPRemoteCommandHandlerStatusCommandFailed
            }
        }
        commandCenter.pauseCommand.addTargetWithHandler {
            if (remoteRouter?.pause(item != null) == true) MPRemoteCommandHandlerStatusSuccess else {
                MPRemoteCommandHandlerStatusCommandFailed
            }
        }
        commandCenter.changePlaybackPositionCommand.addTargetWithHandler { event ->
            val seconds = (event as? MPChangePlaybackPositionCommandEvent)?.positionTime
            if (seconds == null || remoteRouter?.seek(seconds, mutableState.value.durationMs) != true) {
                MPRemoteCommandHandlerStatusCommandFailed
            } else {
                MPRemoteCommandHandlerStatusSuccess
            }
        }
        commandCenter.nextTrackCommand.addTargetWithHandler {
            remoteRouter?.next(); MPRemoteCommandHandlerStatusSuccess
        }
        commandCenter.previousTrackCommand.addTargetWithHandler {
            remoteRouter?.previous(); MPRemoteCommandHandlerStatusSuccess
        }
        commandCenter.stopCommand.addTargetWithHandler {
            remoteRouter?.stop(); MPRemoteCommandHandlerStatusSuccess
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
            completionObserver?.let(NSNotificationCenter.defaultCenter::removeObserver)
            removeTimeObserver()
            generation++
            audioSessionPolicy.onSessionStarted(generation)
            this@IosDirectPlaybackHost.media = media
            started = false
            val newItem = AVPlayerItem(uRL = url)
            item = newItem
            val activePlayer = player ?: AVPlayer(playerItem = newItem).also { player = it }
            if (activePlayer.currentItem !== newItem) activePlayer.replaceCurrentItemWithPlayerItem(newItem)
            completionObserver = NSNotificationCenter.defaultCenter.addObserverForName(
                AVPlayerItemDidPlayToEndTimeNotification, newItem, null
            ) { if (item === newItem) publish(PlaybackState.Completed, media) }
            timeObserver = activePlayer.addPeriodicTimeObserverForInterval(
                CMTimeMakeWithSeconds(1.0, 1_000), null
            ) { time ->
                if (item === newItem) {
                    publishEngineState(cmtimeToMilliseconds(time) ?: 0)
                }
            }
            if (!activateAudioSession()) {
                publish(PlaybackState.Error(PlayerError.SourceUnavailable), media)
                return@withContext
            }
            publish(PlaybackState.Loading, media)
            activePlayer.play()
            updateNowPlaying()
        }
    }

    override fun play() = onMain { audioSessionPolicy.onPlay(); player?.play(); publishEngineState(); updateNowPlaying() }
    override fun pause() = onMain {
        audioSessionPolicy.onPauseOrStop()
        player?.pause()
        publish(PlaybackState.Paused)
        updateNowPlaying()
    }
    override fun seekTo(positionMs: Long) = onMain {
        player?.seekToTime(CMTimeMakeWithSeconds(millisecondsToSeconds(positionMs.coerceAtLeast(0)), 1_000))
        publish(positionMs = positionMs.coerceAtLeast(0))
        updateNowPlaying()
    }
    override fun stop() = onMain {
        generation++
        audioSessionPolicy.onPauseOrStop()
        completionObserver?.let(NSNotificationCenter.defaultCenter::removeObserver)
        completionObserver = null
        removeTimeObserver()
        player?.pause()
        player?.replaceCurrentItemWithPlayerItem(null)
        item = null
        media = null
        started = false
        deactivateAudioSession()
        mutableState.value = PlayerState(sessionGeneration = generation)
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = null
    }

    private fun publishEngineState(positionMs: Long = cmtimeToMilliseconds(player?.currentTime()) ?: 0) {
        val next = when {
            item?.status == AVPlayerItemStatusFailed -> PlaybackState.Error(PlayerError.SourceUnavailable)
            item?.status != AVPlayerItemStatusReadyToPlay -> PlaybackState.Loading
            player?.timeControlStatus == AVPlayerTimeControlStatusPlaying -> PlaybackState.Playing
            player?.timeControlStatus == AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate -> PlaybackState.Buffering
            started -> PlaybackState.Paused
            else -> PlaybackState.Ready
        }
        if (next == PlaybackState.Playing) started = true
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
            sessionGeneration = generation,
        )
        updateNowPlaying()
    }

    private fun onMain(block: () -> Unit) = dispatch_async(dispatch_get_main_queue(), block)

    private fun removeTimeObserver() {
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
        if (interruptionObserver != null || routeObserver != null) return
        val center = NSNotificationCenter.defaultCenter()
        interruptionObserver = center.addObserverForName(AVAudioSessionInterruptionNotification, null, null) { notification ->
            val type = (notification?.userInfo?.get(AVAudioSessionInterruptionTypeKey) as? NSNumber)?.unsignedLongValue
            if (type == AVAudioSessionInterruptionTypeBegan) {
                audioSessionPolicy.onInterruptionBegan(
                    generation,
                    wasPlaying = item != null && mutableState.value.playbackState == PlaybackState.Playing,
                )
                player?.pause()
                publish(PlaybackState.Paused)
                updateNowPlaying()
            } else {
                val shouldResume = ((notification?.userInfo?.get(AVAudioSessionInterruptionOptionKey) as? NSNumber)?.unsignedLongValue ?: 0uL) and AVAudioSessionInterruptionOptionShouldResume != 0uL
                if (audioSessionPolicy.shouldResume(generation, shouldResume) && item != null) {
                    player?.play()
                    publishEngineState()
                }
                audioSessionPolicy.onInterruptionEnded()
            }
        }
        routeObserver = center.addObserverForName(AVAudioSessionRouteChangeNotification, null, null) { notification ->
            val reason = (notification?.userInfo?.get(AVAudioSessionRouteChangeReasonKey) as? NSNumber)?.unsignedLongValue
            if (reason == AVAudioSessionRouteChangeReasonOldDeviceUnavailable) {
                audioSessionPolicy.onRouteLost()
                player?.pause()
                publish(PlaybackState.Paused)
                updateNowPlaying()
            }
        }
    }

    private fun updateNowPlaying() {
        val activeMedia = media ?: return
        val state = mutableState.value
        val projection = nowPlayingProjection(activeMedia, state)
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
