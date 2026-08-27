package kg.dev.shared.feature.player.presentation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.Lifecycle
import kg.dev.shared.core.ui.navigation.PlayerComponent as NavigationPlayerComponent
import kg.dev.shared.feature.history.domain.HistoryRepository
import kg.dev.shared.feature.history.domain.WatchedVideo
import kg.dev.shared.feature.player.PlayableMedia
import kg.dev.shared.feature.player.ProviderPlaybackAdapterRegistry
import kg.dev.shared.feature.player.ProviderPlaybackSession
import kg.dev.shared.feature.player.PlaybackState
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
    val providerPlaybackAdapters: ProviderPlaybackAdapterRegistry = ProviderPlaybackAdapterRegistry.Empty,
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
    private val providerAdapter = (media.source as? PlaybackSource.ProviderControlled)?.let {
        providerPlaybackAdapters[it.reference.provider]
    }
    override val providerPlaybackSession: ProviderPlaybackSession? = providerAdapter?.createSession(media)

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
        when (media.source) {
            is PlaybackSource.Direct -> collectBackendState(videoPlayerController.state)
            is PlaybackSource.ProviderControlled -> providerPlaybackSession?.let { collectBackendState(it.state) }
        }
    }

    override fun play() {
        val providerSession = providerPlaybackSession
        if (media.source is PlaybackSource.ProviderControlled && providerSession == null) return unsupportedProviderPlayback()
        val isFirstLoad = !loadRequested
        if (isFirstLoad) loadRequested = true
        scope.launch {
            when {
                isFirstLoad -> {
                    if (media.source is PlaybackSource.Direct) videoPlayerController.play(media)
                    else providerSession?.load(media)
                    if (!initialSeekConsumed) {
                        initialSeekConsumed = true
                        seekBackendTo(resolvedInitialPositionMs)
                    }
                }
                mutableState.value.isCompleted -> {
                    seekBackendTo(0)
                    resumeBackend()
                }
                else -> resumeBackend()
            }
        }
    }

    override fun pause() {
        if (media.source is PlaybackSource.Direct) videoPlayerController.pause() else providerPlaybackSession?.pause()
        persistProgress()
    }

    override fun seekTo(positionMs: Long) {
        seekBackendTo(positionMs)
    }

    override fun retry() {
        if (media.source is PlaybackSource.ProviderControlled && providerPlaybackSession == null) return unsupportedProviderPlayback()
        if (!loadRequested) {
            play()
        } else {
            scope.launch {
                if (media.source is PlaybackSource.Direct) videoPlayerController.retry()
                else providerPlaybackSession?.retry()
            }
        }
    }

    private fun collectBackendState(backendState: StateFlow<kg.dev.shared.feature.player.PlayerState>) {
        scope.launch {
            var previousPlaybackState: PlaybackState = PlaybackState.Idle
            backendState.collect { playerState ->
                if (released) return@collect
                mutableState.value = PlayerUiState(
                    media = playerState.media ?: media,
                    playbackState = playerState.playbackState,
                    positionMs = playerState.positionMs,
                    durationMs = playerState.durationMs,
                    bufferedPositionMs = playerState.bufferedPositionMs
                )
                if (!playerState.isCompleted) completionPersisted = false
                val shouldPersist = (playerState.isCompleted && !completionPersisted) ||
                    (previousPlaybackState == PlaybackState.Playing &&
                        playerState.playbackState == PlaybackState.Paused) ||
                    playerState.positionMs - lastPersistedPositionMs >= PROGRESS_PERSIST_INTERVAL_MS
                if (shouldPersist) {
                    if (playerState.isCompleted) completionPersisted = true
                    persistProgress(playerState.positionMs, playerState.durationMs, playerState.isCompleted)
                }
                previousPlaybackState = playerState.playbackState
            }
        }
    }

    private fun resumeBackend() {
        if (media.source is PlaybackSource.Direct) videoPlayerController.resume() else providerPlaybackSession?.play()
    }

    private fun seekBackendTo(positionMs: Long) {
        if (media.source is PlaybackSource.Direct) videoPlayerController.seekTo(positionMs)
        else providerPlaybackSession?.seekTo(positionMs)
    }

    private fun unsupportedProviderPlayback() {
        mutableState.value = mutableState.value.copy(
            playbackState = PlaybackState.Error(PlayerError.UnsupportedMedia)
        )
    }

    private fun persistProgress(
        positionMs: Long = mutableState.value.positionMs,
        durationMs: Long? = mutableState.value.durationMs,
        completed: Boolean = mutableState.value.isCompleted,
        releaseAfterPersisting: Boolean = false
    ) {
        if (media.source is PlaybackSource.ProviderControlled && providerPlaybackSession == null) {
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
        if (media.source is PlaybackSource.Direct) videoPlayerController.release() else providerPlaybackSession?.release()
    }

    private companion object {
        const val PROGRESS_PERSIST_INTERVAL_MS = 5_000L
    }
}
