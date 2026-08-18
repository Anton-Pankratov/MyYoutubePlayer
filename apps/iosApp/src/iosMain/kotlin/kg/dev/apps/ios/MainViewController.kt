package kg.dev.apps.ios

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.window.ComposeUIViewController
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import kg.dev.shared.core.common.ApiConfigurationProvider
import kg.dev.shared.core.di.commonModules
import kg.dev.shared.core.ui.navigation.DefaultRootComponent
import kg.dev.shared.feature.search.domain.usecase.SearchChannelsUseCase
import kg.dev.shared.feature.search.presentation.DefaultSearchComponent
import kg.dev.shared.feature.search.presentation.SearchComponent
import kg.dev.shared.feature.search.ui.SharedAppContent
import org.koin.core.context.startKoin
import org.koin.dsl.module
import platform.UIKit.UIViewController

fun MainViewController(youtubeApiKey: String): UIViewController {
    val koin = startKoin { modules(commonModules() + iosModule(youtubeApiKey)) }.koin
    lateinit var rootComponent: DefaultRootComponent<SearchComponent>
    rootComponent = DefaultRootComponent(
        componentContext = DefaultComponentContext(LifecycleRegistry()),
        searchComponentFactory = { childContext -> DefaultSearchComponent(childContext, koin.get<SearchChannelsUseCase>(), onMediaSelected = rootComponent::openMedia) }
    )
    return ComposeUIViewController {
        MaterialTheme { SharedAppContent(rootComponent) }
    }
}

private fun iosModule(youtubeApiKey: String) = module {
    single<ApiConfigurationProvider> { IosApiConfiguration(youtubeApiKey) }
}

private class IosApiConfiguration(
    override val youtubeApiKey: String
) : ApiConfigurationProvider {
}
