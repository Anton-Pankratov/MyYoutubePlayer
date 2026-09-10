package kg.dev.shared.feature.player

/**
 * Process-local AVPlayer intent reducer. AVPlayer has no playWhenReady equivalent, so this
 * distinguishes an intentional pause from a transient AVAudioSession interruption.
 */
internal class IosAudioSessionPolicy {
    private var playbackWanted = false
    private var resumableGeneration: Long? = null

    fun onSessionStarted(generation: Long) {
        playbackWanted = true
        resumableGeneration = null
    }

    fun onPlay() {
        playbackWanted = true
        resumableGeneration = null
    }

    fun onPauseOrStop() {
        playbackWanted = false
        resumableGeneration = null
    }

    fun onInterruptionBegan(generation: Long, wasPlaying: Boolean) {
        resumableGeneration = generation.takeIf { playbackWanted && wasPlaying }
    }

    fun shouldResume(generation: Long, systemAllowsResume: Boolean): Boolean =
        systemAllowsResume && playbackWanted && resumableGeneration == generation

    fun onInterruptionEnded() {
        resumableGeneration = null
    }

    fun onRouteLost() = onPauseOrStop()
}

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
    fun next() = callbacks.next()
    fun previous() = callbacks.previous()
    fun stop() = callbacks.stop()
}
