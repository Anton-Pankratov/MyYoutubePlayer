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
import kg.dev.shared.feature.history.domain.HistoryRepository
import kg.dev.videoplayer.presentation.main.MainScreen
import org.koin.android.ext.android.get
import kg.dev.shared.core.ui.design.MediaAppTheme

class MainActivity : ComponentActivity() {
    private val rootComponent by lazy {
        lateinit var root: DefaultRootComponent<SearchComponent>
        root = DefaultRootComponent(
            componentContext = defaultComponentContext(),
            mediaOpenCoordinator = get<MediaOpenCoordinator>(),
            searchComponentFactory = { childContext ->
                DefaultSearchComponent(childContext, get<SearchChannelsUseCase>(), onMediaSelected = root::openMedia)
            },
            playerComponentFactory = { childContext, configuration ->
                DefaultPlayerComponent(
                    componentContext = childContext,
                    media = PlayableMedia(
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
                    ),
                    videoPlayerController = get<VideoPlayerController>(),
                    historyRepository = get<HistoryRepository>(),
                    initialPositionMs = configuration.startPositionMs,
                    nowEpochMillis = System::currentTimeMillis,
                    providerPlaybackAdapters = ProviderPlaybackAdapterRegistry(
                        listOf(AndroidYouTubePlaybackAdapter)
                    )
                )
            }
        )
        return@lazy root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MediaAppTheme { MainScreen(rootComponent) } }
    }
}
