package kg.dev.apps.ios

import androidx.compose.ui.window.ComposeUIViewController
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import kg.dev.shared.core.common.ApiConfigurationProvider
import kg.dev.shared.core.di.commonModules
import kg.dev.shared.core.storage.db.PlayerDatabase
import kg.dev.shared.core.ui.navigation.DefaultRootComponent
import kg.dev.shared.feature.search.domain.usecase.SearchChannelsUseCase
import kg.dev.shared.feature.search.presentation.DefaultSearchComponent
import kg.dev.shared.feature.search.presentation.SearchComponent
import kg.dev.shared.appshell.SharedAppContent
import kg.dev.shared.feature.history.domain.HistoryRepository
import kg.dev.shared.feature.home.presentation.DefaultHomeComponent
import kg.dev.shared.feature.home.presentation.HomeMediaAvailability
import kg.dev.shared.feature.player.DefaultMediaOpenCoordinator
import kg.dev.shared.feature.player.IosUnavailableVideoPlayerController
import kg.dev.shared.feature.player.PlayableMedia
import kg.dev.shared.feature.player.PlaybackSource
import kg.dev.shared.feature.player.PlaybackSourceResolverRegistry
import kg.dev.shared.feature.player.ProviderPlaybackAdapterRegistry
import kg.dev.shared.feature.player.presentation.DefaultPlayerComponent
import kg.dev.shared.feature.player.ui.IosYouTubePlaybackAdapter
import kg.dev.shared.core.common.media.MediaCatalogItem
import kg.dev.shared.core.common.media.MediaProviderId
import kg.dev.shared.core.common.media.MediaReference
import org.koin.core.context.startKoin
import org.koin.dsl.module
import platform.UIKit.UIViewController
import kg.dev.shared.core.ui.design.MediaAppTheme

fun MainViewController(youtubeApiKey: String): UIViewController {
    val koin = startKoin { modules(commonModules() + iosModule(youtubeApiKey)) }.koin
    lateinit var rootComponent: DefaultRootComponent<SearchComponent>
    rootComponent = DefaultRootComponent(
        componentContext = DefaultComponentContext(LifecycleRegistry()),
        mediaOpenCoordinator = DefaultMediaOpenCoordinator(PlaybackSourceResolverRegistry(emptySet())),
        searchComponentFactory = { childContext -> DefaultSearchComponent(childContext, koin.get<SearchChannelsUseCase>(), onMediaSelected = rootComponent::openMedia) },
        playerComponentFactory = { childContext, configuration ->
            val reference = MediaReference(MediaProviderId(configuration.providerId), configuration.externalId)
            val source = if (configuration.playbackKind == "direct") {
                PlaybackSource.Direct(configuration.directUri.orEmpty(), configuration.mimeType)
            } else {
                PlaybackSource.ProviderControlled(reference)
            }
            DefaultPlayerComponent(
                componentContext = childContext,
                media = PlayableMedia(
                    MediaCatalogItem(
                        reference = reference,
                        title = configuration.title ?: configuration.externalId,
                        thumbnailUrl = configuration.thumbnailUrl,
                        authorTitle = configuration.authorTitle,
                        durationMs = configuration.catalogDurationMs
                    ),
                    source
                ),
                videoPlayerController = IosUnavailableVideoPlayerController(),
                historyRepository = koin.get<HistoryRepository>(),
                initialPositionMs = configuration.startPositionMs,
                nowEpochMillis = { kotlin.system.getTimeMillis() },
                providerPlaybackAdapters = ProviderPlaybackAdapterRegistry(listOf(IosYouTubePlaybackAdapter))
            )
        }
    )
    return ComposeUIViewController {
        MediaAppTheme {
            SharedAppContent(
                rootComponent = rootComponent,
                homeComponentFactory = { context, selected, _ ->
                    DefaultHomeComponent(
                        componentContext = context,
                        historyRepository = koin.get<HistoryRepository>(),
                        mediaAvailability = HomeMediaAvailability { false },
                        onItemSelected = selected
                    )
                },
                playerContent = { component, modifier ->
                    kg.dev.shared.feature.player.ui.PlayerContent(
                        component = component as DefaultPlayerComponent,
                        modifier = modifier,
                        providerAdapters = component.providerPlaybackAdapters
                    )
                }
            )
        }
    }
}

private fun iosModule(youtubeApiKey: String) = module {
    single<ApiConfigurationProvider> { IosApiConfiguration(youtubeApiKey) }
    single<SqlDriver> { NativeSqliteDriver(PlayerDatabase.Schema, "youtube-player.db") }
}

private class IosApiConfiguration(
    override val youtubeApiKey: String
) : ApiConfigurationProvider {
}
