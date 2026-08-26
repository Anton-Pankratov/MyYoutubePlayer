package kg.dev.apps.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import kg.dev.shared.core.common.ApiConfigurationProvider
import kg.dev.shared.core.di.commonModules
import kg.dev.shared.core.ui.navigation.DefaultRootComponent
import kg.dev.shared.feature.search.domain.usecase.SearchChannelsUseCase
import kg.dev.shared.feature.search.presentation.DefaultSearchComponent
import kg.dev.shared.feature.search.presentation.SearchComponent
import kg.dev.shared.appshell.SharedAppContent
import kg.dev.shared.feature.player.DefaultMediaOpenCoordinator
import kg.dev.shared.feature.player.PlaybackSourceResolverRegistry
import kg.dev.shared.feature.player.ui.ProviderPlayerContent
import kotlinx.browser.window
import org.koin.core.context.startKoin
import org.koin.dsl.module
import kg.dev.shared.core.ui.design.MediaAppTheme

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val koin = startKoin { modules(commonModules() + webModule()) }.koin
    lateinit var rootComponent: DefaultRootComponent<SearchComponent>
    rootComponent = DefaultRootComponent(
        componentContext = DefaultComponentContext(LifecycleRegistry()),
        mediaOpenCoordinator = DefaultMediaOpenCoordinator(PlaybackSourceResolverRegistry(emptySet())),
        searchComponentFactory = { childContext -> DefaultSearchComponent(childContext, koin.get<SearchChannelsUseCase>(), onMediaSelected = rootComponent::openMedia) }
    )
    CanvasBasedWindow("Luma — Media Library") {
        MediaAppTheme {
            SharedAppContent(rootComponent, playerContent = { component, modifier ->
                ProviderPlayerContent(component, modifier)
            })
        }
    }
}

private fun webModule() = module {
    single<ApiConfigurationProvider> { WebApiConfiguration }
}

private object WebApiConfiguration : ApiConfigurationProvider {
    override val youtubeApiKey: String = window.asDynamic().YOUTUBE_DATA_API_V3_API_KEY as? String ?: ""
}
