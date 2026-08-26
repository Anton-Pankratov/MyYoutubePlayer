package kg.dev.shared.feature.player.presentation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.Lifecycle
import kg.dev.shared.core.ui.navigation.PlayerComponent as NavigationPlayerComponent
import kg.dev.shared.feature.history.domain.HistoryRepository
import kg.dev.shared.feature.history.domain.WatchedVideo
import kg.dev.shared.feature.player.PlayableMedia
import kg.dev.shared.feature.player.PlaybackSource
import kg.dev.shared.feature.player.PlayerError
import kg.dev.shared.feature.player.VideoPlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

class DefaultPlayerComponent(
    componentContext: ComponentContext,
    private val media: PlayableMedia,
    val videoPlayerController: VideoPlayerController,
    private val historyRepository: HistoryRepository,
    private val initialPositionMs: Long = 0,
    private val nowEpochMillis: () -> Long,
    coroutineContext: CoroutineContext = Dispatchers.Default
) : PlayerComponent, NavigationPlayerComponent, ComponentContext by componentContext {
    private val resolvedInitialPositionMs = initialPositionMs.coerceAtLeast(0)
    private val scope = CoroutineScope(SupervisorJob() + coroutineContext)
    private val mutableState = MutableStateFlow(PlayerUiState(media = media, positionMs = resolvedInitialPositionMs))
    override val state: StateFlow<PlayerUiState> = mutableState.asStateFlow()
    private var lastPersistedPositionMs = resolvedInitialPositionMs
    private var loadRequested = false
    private var initialSeekConsumed = resolvedInitialPositionMs == 0L
    private var completionPersisted = false
    private var released = false

    override val mediaId: String get() = media.catalogItem.reference.externalId
    override val providerId: String get() = media.catalogItem.reference.provider.value
    override val title: String get() = media.catalogItem.title
    override val thumbnailUrl: String? get() = media.catalogItem.thumbnailUrl
    override val authorTitle: String? get() = media.catalogItem.authorTitle
    override val catalogDurationMs: Long? get() = media.catalogItem.durationMs
    override val playbackKind: String
        get() = if (media.source is PlaybackSource.Direct) "direct" else "provider-controlled"
    override val directUri: String? get() = (media.source as? PlaybackSource.Direct)?.uri
    override val mimeType: String? get() = (media.source as? PlaybackSource.Direct)?.mimeType
    override val startPositionMs: Long get() = resolvedInitialPositionMs

    init {
        lifecycle.subscribe(object : Lifecycle.Callbacks {
            override fun onDestroy() {
                persistProgress(releaseAfterPersisting = true)
            }
        })
        scope.launch {
            var wasPlaying = false
            videoPlayerController.state.collect { playerState ->
                mutableState.value = PlayerUiState(
                    media = playerState.media ?: media,
                    isPlaying = playerState.isPlaying,
                    positionMs = playerState.positionMs,
                    durationMs = playerState.durationMs,
                    bufferedPositionMs = playerState.bufferedPositionMs,
                    error = playerState.error ?: if (playerState.media == null) mutableState.value.error else null,
                    isCompleted = playerState.isCompleted
                )
                if (!playerState.isCompleted) completionPersisted = false
                val shouldPersist = (playerState.isCompleted && !completionPersisted) ||
                    (wasPlaying && !playerState.isPlaying) ||
                    playerState.positionMs - lastPersistedPositionMs >= PROGRESS_PERSIST_INTERVAL_MS
                if (shouldPersist) {
                    if (playerState.isCompleted) completionPersisted = true
                    persistProgress(playerState.positionMs, playerState.durationMs, playerState.isCompleted)
                }
                wasPlaying = playerState.isPlaying
            }
        }
    }

    override fun play() {
        if (media.source !is PlaybackSource.Direct) {
            mutableState.value = mutableState.value.copy(error = PlayerError.UnsupportedMedia)
            return
        }
        val isFirstLoad = !loadRequested
        if (isFirstLoad) loadRequested = true
        scope.launch {
            when {
                isFirstLoad -> {
                    videoPlayerController.play(media)
                    if (!initialSeekConsumed) {
                        initialSeekConsumed = true
                        videoPlayerController.seekTo(resolvedInitialPositionMs)
                    }
                }
                mutableState.value.isCompleted -> {
                    videoPlayerController.seekTo(0)
                    videoPlayerController.resume()
                }
                else -> videoPlayerController.resume()
            }
        }
    }

    override fun pause() {
        videoPlayerController.pause()
        persistProgress()
    }

    override fun seekTo(positionMs: Long) {
        videoPlayerController.seekTo(positionMs)
    }

    override fun retry() {
        if (media.source !is PlaybackSource.Direct) {
            mutableState.value = mutableState.value.copy(error = PlayerError.UnsupportedMedia)
            return
        }
        if (!loadRequested) {
            play()
        } else {
            scope.launch { videoPlayerController.retry() }
        }
    }

    private fun persistProgress(
        positionMs: Long = mutableState.value.positionMs,
        durationMs: Long? = mutableState.value.durationMs,
        completed: Boolean = mutableState.value.isCompleted,
        releaseAfterPersisting: Boolean = false
    ) {
        if (media.source !is PlaybackSource.Direct) {
            if (releaseAfterPersisting) releaseAndCancel()
            return
        }
        val usableDurationMs = durationMs?.takeIf { it > 0 }
        val savedPosition = if (completed && usableDurationMs != null) {
            usableDurationMs
        } else {
            positionMs.coerceAtLeast(0)
        }
        lastPersistedPositionMs = savedPosition
        scope.launch {
            try {
                historyRepository.save(
                    WatchedVideo(
                        reference = media.catalogItem.reference,
                        title = media.catalogItem.title,
                        thumbnailUrl = media.catalogItem.thumbnailUrl,
                        durationMs = durationMs,
                        positionMs = savedPosition,
                        watchedAtEpochMs = nowEpochMillis()
                    )
                )
            } finally {
                if (releaseAfterPersisting) releaseAndCancel()
            }
        }
    }

    private fun releaseAndCancel() {
        releaseOnce()
        scope.cancel()
    }

    private fun releaseOnce() {
        if (released) return
        released = true
        videoPlayerController.release()
    }

    private companion object {
        const val PROGRESS_PERSIST_INTERVAL_MS = 5_000L
    }
}
