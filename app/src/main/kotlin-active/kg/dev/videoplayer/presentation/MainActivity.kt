package kg.dev.videoplayer.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.arkivanov.decompose.defaultComponentContext
import kg.dev.shared.core.ui.navigation.DefaultRootComponent
import kg.dev.shared.feature.search.domain.usecase.SearchChannelsUseCase
import kg.dev.shared.feature.search.presentation.DefaultSearchComponent
import kg.dev.shared.feature.search.presentation.SearchComponent
import kg.dev.shared.core.common.media.MediaCatalogItem
import kg.dev.shared.core.common.media.MediaProviderId
import kg.dev.shared.core.common.media.MediaReference
import kg.dev.shared.feature.player.PlayableMedia
import kg.dev.shared.feature.player.PlaybackSource
import kg.dev.shared.core.ui.navigation.MediaOpenCoordinator
import kg.dev.shared.feature.player.VideoPlayerController
import kg.dev.shared.feature.player.ProviderPlaybackAdapterRegistry
import kg.dev.shared.feature.player.presentation.DefaultPlayerComponent
import kg.dev.shared.feature.player.ui.AndroidYouTubePlaybackAdapter
import kg.dev.shared.feature.player.directBackgroundEligibility
import kg.dev.shared.feature.player.DirectAudioApplicationCallbacks
import kg.dev.shared.feature.player.DirectAudioApplicationCallbackGateway
import kg.dev.shared.feature.player.DirectAudioSessionCoordinator
import kg.dev.shared.core.common.media.DirectBackgroundEligibility
import kg.dev.shared.feature.history.domain.HistoryRepository
import kg.dev.shared.feature.player.library.SavedMediaRepository
import kg.dev.videoplayer.presentation.main.MainScreen
import org.koin.android.ext.android.get
import kg.dev.shared.core.ui.design.MediaAppTheme
import kg.dev.videoplayer.playback.AndroidServiceDirectPlaybackHost

class MainActivity : ComponentActivity() {
    private val directAudioHost by lazy { get<AndroidServiceDirectPlaybackHost>() }
    private val directAudioCoordinator by lazy { get<DirectAudioSessionCoordinator>() }
    private val directAudioCallbacks by lazy { get<DirectAudioApplicationCallbackGateway>() }
    private lateinit var callbackOwner: DirectAudioApplicationCallbacks
    private val rootComponent by lazy {
        lateinit var root: DefaultRootComponent<SearchComponent>
        root = DefaultRootComponent(
            componentContext = defaultComponentContext(),
            mediaOpenCoordinator = get<MediaOpenCoordinator>(),
            canRetainEligibleDirectSession = { directAudioHost.capabilities.supportsBackgroundPlayback },
            onEligiblePlayerUiDetached = { directAudioHost.detachUi() },
            onStopPlayback = directAudioCoordinator::stop,
            searchComponentFactory = { childContext ->
                DefaultSearchComponent(childContext, get<SearchChannelsUseCase>(), onMediaSelected = root::openMedia)
            },
            playerComponentFactory = { childContext, configuration ->
                val media = PlayableMedia(
                    MediaCatalogItem(
                        reference = MediaReference(MediaProviderId(configuration.providerId), configuration.externalId),
                        title = configuration.title ?: configuration.externalId,
                        thumbnailUrl = configuration.thumbnailUrl,
                        authorTitle = configuration.authorTitle,
                        durationMs = configuration.catalogDurationMs
                    ),
                    if (configuration.playbackKind == "direct") PlaybackSource.Direct(
                        configuration.directUri.orEmpty(), configuration.mimeType
                    ) else PlaybackSource.ProviderControlled(
                        MediaReference(MediaProviderId(configuration.providerId), configuration.externalId)
                    )
                )
                val serviceHost = directAudioHost.takeIf {
                    media.directBackgroundEligibility() == DirectBackgroundEligibility.Eligible
                }
                DefaultPlayerComponent(
                    componentContext = childContext,
                    media = media,
                    videoPlayerController = get<VideoPlayerController>(),
                    historyRepository = get<HistoryRepository>(),
                    initialPositionMs = configuration.startPositionMs,
                    nowEpochMillis = System::currentTimeMillis,
                    providerPlaybackAdapters = ProviderPlaybackAdapterRegistry(
                        listOf(AndroidYouTubePlaybackAdapter)
                    ),
                    savedMediaRepository = get<SavedMediaRepository>(),
                    playbackQueue = root.playbackQueue,
                    onQueueNext = if (serviceHost != null) directAudioCoordinator::next else root::queueNext,
                    onQueuePrevious = if (serviceHost != null) directAudioCoordinator::previous else root::queuePrevious,
                    onNaturalCompletion = root::onQueueItemCompleted,
                    directPlaybackHost = serviceHost,
                )
            }
        )
        callbackOwner = object : DirectAudioApplicationCallbacks {
            override suspend fun onNaturalCompletion(reference: MediaReference) {
                root.onQueueItemCompleted(reference)
                if (!root.playbackQueue.value.isActive) directAudioCoordinator.finishCompletedSession(reference)
            }
            override fun onNext() = root.queueNext()
            override fun onPrevious() = root.queuePrevious()
            override fun onStop() = root.stopPlayback()
        }
        directAudioCallbacks.replace(callbackOwner, callbackOwner)
        return@lazy root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MediaAppTheme { MainScreen(rootComponent) } }
    }

    override fun onDestroy() {
        if (::callbackOwner.isInitialized) directAudioCallbacks.clear(callbackOwner)
        super.onDestroy()
    }
}
