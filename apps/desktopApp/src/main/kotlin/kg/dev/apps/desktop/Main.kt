package kg.dev.apps.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
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
import kg.dev.shared.feature.home.presentation.DefaultHomeComponent
import kg.dev.shared.feature.home.presentation.HomeMediaAvailability
import kg.dev.shared.feature.history.domain.HistoryRepository
import kg.dev.shared.feature.player.DefaultMediaOpenCoordinator
import kg.dev.shared.feature.player.PlaybackSourceResolverRegistry
import kg.dev.shared.feature.player.ui.ProviderPlayerContent
import org.koin.core.context.startKoin
import org.koin.dsl.module
import java.io.File
import kg.dev.shared.core.ui.design.MediaAppTheme

fun main() {
    val koin = startKoin { modules(commonModules() + desktopModule()) }.koin
    val lifecycle = LifecycleRegistry()
    lifecycle.onCreate()
    lateinit var rootComponent: DefaultRootComponent<SearchComponent>
    rootComponent = DefaultRootComponent(
        componentContext = DefaultComponentContext(lifecycle),
        mediaOpenCoordinator = DefaultMediaOpenCoordinator(PlaybackSourceResolverRegistry(emptySet())),
        searchComponentFactory = { childContext -> DefaultSearchComponent(childContext, koin.get<SearchChannelsUseCase>(), onMediaSelected = rootComponent::openMedia) }
    )

    application {
        Window(
            onCloseRequest = {
                lifecycle.onDestroy()
                koin.get<io.ktor.client.HttpClient>().close()
                koin.get<SqlDriver>().close()
                exitApplication()
            },
            title = "Luma — Media Library"
        ) {
            MediaAppTheme {
                SharedAppContent(
                    rootComponent = rootComponent,
                    homeComponentFactory = { context, selected ->
                        DefaultHomeComponent(
                            componentContext = context,
                            historyRepository = koin.get<HistoryRepository>(),
                            mediaAvailability = HomeMediaAvailability { false },
                            onItemSelected = selected
                        )
                    },
                    playerContent = { component, modifier -> ProviderPlayerContent(component, modifier) }
                )
            }
        }
    }
}

private fun desktopModule() = module {
    single<ApiConfigurationProvider> { DesktopApiConfiguration }
    single<SqlDriver> { createDesktopSqlDriver() }
}

private fun createDesktopSqlDriver(): SqlDriver {
    val databaseDirectory = File(System.getProperty("user.home"), ".my-youtube-player")
    databaseDirectory.mkdirs()
    val driver = JdbcSqliteDriver("jdbc:sqlite:${File(databaseDirectory, "youtube-player.db").absolutePath}")
    val version = driver.executeQuery(
        identifier = null,
        sql = "PRAGMA user_version",
        mapper = { cursor ->
            QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L)
        },
        parameters = 0
    ).value

    when {
        version == 0L -> {
            PlayerDatabase.Schema.create(driver)
            driver.execute(null, "PRAGMA user_version = ${PlayerDatabase.Schema.version}", 0)
        }
        version < PlayerDatabase.Schema.version -> {
            PlayerDatabase.Schema.migrate(driver, version, PlayerDatabase.Schema.version)
            driver.execute(null, "PRAGMA user_version = ${PlayerDatabase.Schema.version}", 0)
        }
    }

    return driver
}

private object DesktopApiConfiguration : ApiConfigurationProvider {
    override val youtubeApiKey: String = System.getenv("YOUTUBE_DATA_API_V3_API_KEY").orEmpty()
}
