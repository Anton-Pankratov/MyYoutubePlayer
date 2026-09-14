package kg.dev.shared.feature.player

/**
 * The sole native-media generation gate. Every item-specific AVPlayer callback captures one
 * token and must verify it before it can project state into the shared host.
 */
internal class IosHostSessionGeneration {
    private var currentGeneration = 0L
    private var completionClaimedForGeneration: Long? = null

    fun replaceOrInvalidate(): Long {
        currentGeneration += 1
        completionClaimedForGeneration = null
        return currentGeneration
    }

    fun current(): Long = currentGeneration

    fun isCurrent(generation: Long): Boolean = currentGeneration == generation

    fun claimCompletion(generation: Long): Boolean {
        if (!isCurrent(generation) || completionClaimedForGeneration == generation) return false
        completionClaimedForGeneration = generation
        return true
    }
}

/**
 * Process-local AVPlayer intent reducer. AVPlayer has no playWhenReady equivalent, so this
 * distinguishes an intentional pause from a transient AVAudioSession interruption.
 */
internal class IosAudioSessionPolicy {
    private var activeGeneration: Long? = null
    private var playbackWanted = false
    private var interruptionGeneration: Long? = null
    private var resumableGeneration: Long? = null

    fun onSessionStarted(generation: Long) {
        activeGeneration = generation
        playbackWanted = true
        interruptionGeneration = null
        resumableGeneration = null
    }

    fun onPlay(generation: Long) {
        if (activeGeneration != generation) return
        playbackWanted = true
        interruptionGeneration = null
        resumableGeneration = null
    }

    fun onPause(generation: Long) {
        if (activeGeneration != generation) return
        playbackWanted = false
        interruptionGeneration = null
        resumableGeneration = null
    }

    fun onStop(generation: Long) {
        if (activeGeneration != generation) return
        playbackWanted = false
        activeGeneration = null
        interruptionGeneration = null
        resumableGeneration = null
    }

    /** Returns true only for the first valid begin notification for the current session. */
    fun onInterruptionBegan(generation: Long, wasPlaying: Boolean): Boolean {
        if (activeGeneration != generation || interruptionGeneration != null) return false
        interruptionGeneration = generation
        resumableGeneration = generation.takeIf { playbackWanted && wasPlaying }
        return true
    }

    /** Atomically consumes the resume claim so duplicate end notifications cannot replay media. */
    fun claimResume(generation: Long, systemAllowsResume: Boolean): Boolean {
        if (interruptionGeneration != generation) return false
        val shouldResume = systemAllowsResume &&
            activeGeneration == generation &&
            playbackWanted &&
            resumableGeneration == generation
        interruptionGeneration = null
        resumableGeneration = null
        return shouldResume
    }

    /** Route loss is an explicit V1 no-auto-resume pause. */
    fun onRouteLost(generation: Long): Boolean {
        if (activeGeneration != generation) return false
        playbackWanted = false
        interruptionGeneration = null
        resumableGeneration = null
        return true
    }

    /** Media-services reset is conservative: retain media identity but require explicit Play. */
    fun onMediaServicesReset(generation: Long) {
        if (activeGeneration != generation) return
        playbackWanted = false
        interruptionGeneration = null
        resumableGeneration = null
    }
}

internal enum class IosRouteChangeAction {
    PauseAndCancelResume,
    Ignore,
}

internal fun iosRouteChangeAction(isOldDeviceUnavailable: Boolean): IosRouteChangeAction =
    if (isOldDeviceUnavailable) IosRouteChangeAction.PauseAndCancelResume else IosRouteChangeAction.Ignore

/** Shared by AVPlayer status and failure-notification paths: failure is never completion. */
internal fun iosCurrentItemFailureState(): PlaybackState = PlaybackState.Error(PlayerError.SourceUnavailable)

internal data class IosNowPlayingProjection(
    val title: String,
    val author: String?,
    val durationSeconds: Double?,
    val elapsedSeconds: Double,
    val playbackRate: Double,
)

internal fun nowPlayingProjection(media: PlayableMedia, state: PlayerState): IosNowPlayingProjection =
    IosNowPlayingProjection(
        title = media.catalogItem.title,
        author = media.catalogItem.authorTitle,
        durationSeconds = state.durationMs?.takeIf { it > 0 }?.div(1_000.0),
        elapsedSeconds = state.positionMs.coerceAtLeast(0) / 1_000.0,
        playbackRate = if (state.playbackState == PlaybackState.Playing) 1.0 else 0.0,
    )

/** Native command handlers delegate through this testable boundary; it owns no queue. */
internal class IosRemoteCommandRouter(private val callbacks: DirectPlaybackCommandCallbacks) {
    fun play(active: Boolean): Boolean = active.also { if (it) callbacks.play() }
    fun pause(active: Boolean): Boolean = active.also { if (it) callbacks.pause() }
    fun seek(seconds: Double, durationMs: Long?): Boolean {
        if (!seconds.isFinite() || seconds < 0.0) return false
        val requestedMs = (seconds * 1_000.0).toLong()
        callbacks.seekTo(durationMs?.takeIf { it > 0 }?.let { requestedMs.coerceAtMost(it) } ?: requestedMs)
        return true
    }
    fun next(active: Boolean): Boolean = active.also { if (it) callbacks.next() }
    fun previous(active: Boolean): Boolean = active.also { if (it) callbacks.previous() }
    /** Stop is intentionally idempotent: an already-empty host reports success without a callback. */
    fun stop(active: Boolean): Boolean {
        if (active) callbacks.stop()
        return true
    }
}
