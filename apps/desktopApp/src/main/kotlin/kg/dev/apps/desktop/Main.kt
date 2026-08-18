package kg.dev.apps.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import kg.dev.shared.core.common.ApiConfigurationProvider
import kg.dev.shared.core.di.commonModules
import kg.dev.shared.core.ui.navigation.DefaultRootComponent
import kg.dev.shared.feature.search.domain.usecase.SearchChannelsUseCase
import kg.dev.shared.feature.search.presentation.DefaultSearchComponent
import kg.dev.shared.feature.search.presentation.SearchComponent
import kg.dev.shared.feature.search.ui.SharedAppContent
import kg.dev.shared.feature.history.presentation.DefaultHistoryComponent
import kg.dev.shared.feature.history.domain.HistoryRepository
import org.koin.core.context.startKoin
import org.koin.dsl.module

fun main() {
    val koin = startKoin { modules(commonModules() + desktopModule()) }.koin
    val lifecycle = LifecycleRegistry()
    lifecycle.onCreate()
    lateinit var rootComponent: DefaultRootComponent<SearchComponent>
    rootComponent = DefaultRootComponent(
        componentContext = DefaultComponentContext(lifecycle),
        searchComponentFactory = { childContext -> DefaultSearchComponent(childContext, koin.get<SearchChannelsUseCase>(), onMediaSelected = rootComponent::openMedia) }
    )

    application {
        Window(
            onCloseRequest = {
                lifecycle.onDestroy()
                koin.get<io.ktor.client.HttpClient>().close()
                exitApplication()
            },
            title = "YouTube Search"
        ) {
            MaterialTheme { SharedAppContent(rootComponent, historyComponentFactory = { context, selected ->
                DefaultHistoryComponent(context, koin.get<HistoryRepository>(), onItemSelected = selected)
            }) }
        }
    }
}

private fun desktopModule() = module {
    single<ApiConfigurationProvider> { DesktopApiConfiguration }
}

private object DesktopApiConfiguration : ApiConfigurationProvider {
    override val youtubeApiKey: String = System.getenv("YOUTUBE_DATA_API_V3_API_KEY").orEmpty()
}
