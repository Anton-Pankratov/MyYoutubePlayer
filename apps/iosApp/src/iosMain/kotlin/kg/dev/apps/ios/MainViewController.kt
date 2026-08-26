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
import kg.dev.shared.feature.player.PlaybackSourceResolverRegistry
import kg.dev.shared.feature.player.ui.ProviderPlayerContent
import kg.dev.shared.feature.player.ui.ProviderPlaybackAdapter
import kg.dev.shared.feature.player.ui.ProviderPlaybackAdapterRegistry
import kg.dev.shared.feature.player.ui.IosYouTubePlayer
import kg.dev.shared.core.common.media.MediaProviders
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
        searchComponentFactory = { childContext -> DefaultSearchComponent(childContext, koin.get<SearchChannelsUseCase>(), onMediaSelected = rootComponent::openMedia) }
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
                    ProviderPlayerContent(
                        component = component,
                        modifier = modifier,
                        providerAdapters = ProviderPlaybackAdapterRegistry(
                            listOf(
                                ProviderPlaybackAdapter(MediaProviders.YouTube) { reference, position, surfaceModifier ->
                                IosYouTubePlayer(reference.externalId, position, surfaceModifier)
                                }
                            )
                        )
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
