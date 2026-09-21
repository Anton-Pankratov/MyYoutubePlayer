package kg.dev.shared.feature.player

import kg.dev.shared.core.common.media.MediaReference
import kg.dev.shared.core.common.media.DirectBackgroundEligibility
import kg.dev.shared.feature.history.domain.HistoryRepository
import kg.dev.shared.feature.history.domain.WatchedVideo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.CoroutineContext

interface DirectAudioApplicationCallbacks {
    suspend fun onNaturalCompletion(reference: MediaReference)
    fun onNext()
    fun onPrevious()
    fun onStop()
}

/** Replaceable application callback owner; playback infrastructure never retains Root directly. */
class DirectAudioApplicationCallbackGateway {
    private data class Registration(val owner: Any, val callbacks: DirectAudioApplicationCallbacks)
    private val registration = MutableStateFlow<Registration?>(null)

    fun replace(owner: Any, callbacks: DirectAudioApplicationCallbacks) {
        registration.value = Registration(owner, callbacks)
    }

    fun clear(owner: Any) {
        if (registration.value?.owner === owner) registration.value = null
    }

    suspend fun naturalCompletion(reference: MediaReference) {
        registration.value?.callbacks?.onNaturalCompletion(reference)
    }

    fun next(): Boolean = registration.value?.callbacks?.let { it.onNext(); true } ?: false
    fun previous(): Boolean = registration.value?.callbacks?.let { it.onPrevious(); true } ?: false
    fun stop(): Boolean = registration.value?.callbacks?.let { it.onStop(); true } ?: false
}

/**
 * Process-scoped History and completion authority for service-hosted Direct audio.
 * It owns no platform player and no application queue.
 */
class DirectAudioSessionCoordinator(
    private val host: DirectPlaybackHost,
    private val historyRepository: HistoryRepository,
    private val callbacks: DirectAudioApplicationCallbackGateway,
    private val nowEpochMillis: () -> Long,
    coroutineContext: CoroutineContext = Dispatchers.Default,
) {
    private data class ActiveSession(
        val generation: Long,
        val media: PlayableMedia,
        var playbackState: PlaybackState,
        var positionMs: Long,
        var durationMs: Long?,
        var lastPersistedPositionMs: Long,
        var completionClaimed: Boolean = false,
    )

    private val scope = CoroutineScope(SupervisorJob() + coroutineContext)
    private val sessionMutex = Mutex()
    private var active: ActiveSession? = null
    private var invalidatedThroughGeneration = 0L

    init {
        scope.launch { host.state.collect(::onHostState) }
    }

    fun next() {
        scope.launch {
            sessionMutex.withLock {
                persistCurrentProgress()
                callbacks.next()
            }
        }
    }

    fun previous() {
        scope.launch {
            sessionMutex.withLock {
                persistCurrentProgress()
                callbacks.previous()
            }
        }
    }

    /** Persists the active Direct-audio position before Root replaces it through queue selection. */
    suspend fun persistProgressForQueueSelection() {
        sessionMutex.withLock { persistCurrentProgress() }
    }

    /** Called by Root's explicit stop boundary. */
    fun stop() {
        scope.launch {
            sessionMutex.withLock {
                persistCurrentProgress()
                invalidateCurrentSession()
                host.stop()
            }
        }
    }

    /** Stops the native Direct-audio session at a foreground-only queue boundary without clearing the application queue. */
    suspend fun stopForForegroundPlayback() {
        sessionMutex.withLock {
            invalidateCurrentSession()
            host.stop()
        }
    }

    /** Called by a platform/system Stop command. */
    fun requestStop() {
        if (!callbacks.stop()) stop()
    }

    /** Releases a naturally completed final item after Root has exhausted its queue. */
    fun finishCompletedSession(reference: MediaReference) {
        scope.launch {
            sessionMutex.withLock {
                val session = active ?: return@withLock
                if (!session.completionClaimed || session.media.catalogItem.reference != reference) return@withLock
                invalidateCurrentSession()
                host.stop()
            }
        }
    }

    fun close() {
        scope.cancel()
    }

    private suspend fun onHostState(state: PlayerState) {
        sessionMutex.withLock { handleHostState(state) }
    }

    private suspend fun handleHostState(state: PlayerState) {
        val generation = state.sessionGeneration
        if (generation <= invalidatedThroughGeneration) return
        val media = state.media
        if (media == null || media.directBackgroundEligibility() != DirectBackgroundEligibility.Eligible) return

        val current = active
        val session = when {
            current == null || generation > current.generation -> ActiveSession(
                generation = generation,
                media = media,
                playbackState = state.playbackState,
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                lastPersistedPositionMs = state.positionMs,
            ).also { active = it }
            generation < current.generation || media.catalogItem.reference != current.media.catalogItem.reference -> return
            else -> current
        }

        val previousPlaybackState = session.playbackState
        session.playbackState = state.playbackState
        session.positionMs = state.positionMs.coerceAtLeast(0)
        session.durationMs = state.durationMs

        when {
            state.isCompleted -> complete(session)
            previousPlaybackState == PlaybackState.Playing && state.playbackState == PlaybackState.Paused ->
                persist(session, completed = false)
            session.positionMs - session.lastPersistedPositionMs >= PROGRESS_PERSIST_INTERVAL_MS ->
                persist(session, completed = false)
        }
    }

    private suspend fun complete(session: ActiveSession) {
        if (active !== session || session.completionClaimed) return
        session.completionClaimed = true
        persist(session, completed = true)
        if (active !== session || session.generation <= invalidatedThroughGeneration) return
        callbacks.naturalCompletion(session.media.catalogItem.reference)
    }

    private suspend fun persistCurrentProgress() {
        active?.let { persist(it, completed = false) }
    }

    private suspend fun persist(session: ActiveSession, completed: Boolean) {
        val usableDuration = session.durationMs?.takeIf { it > 0 }
        val savedPosition = if (completed && usableDuration != null) usableDuration else session.positionMs
        historyRepository.save(
            WatchedVideo(
                reference = session.media.catalogItem.reference,
                title = session.media.catalogItem.title,
                thumbnailUrl = session.media.catalogItem.thumbnailUrl,
                positionMs = savedPosition.coerceAtLeast(0),
                durationMs = session.durationMs,
                watchedAtEpochMs = nowEpochMillis(),
            )
        )
        session.lastPersistedPositionMs = savedPosition
    }

    private fun invalidateCurrentSession() {
        active?.let { invalidatedThroughGeneration = maxOf(invalidatedThroughGeneration, it.generation) }
        active = null
    }

    private companion object {
        const val PROGRESS_PERSIST_INTERVAL_MS = 5_000L
    }
}
