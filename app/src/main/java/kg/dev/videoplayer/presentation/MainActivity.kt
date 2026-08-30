package kg.dev.videoplayer.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import kg.dev.videoplayer.presentation.main.MainScreen
import kg.dev.videoplayer.ui.theme.MyYoutubePlayerTheme
import com.arkivanov.decompose.defaultComponentContext
import kg.dev.shared.core.ui.navigation.DefaultRootComponent
import kg.dev.shared.feature.search.domain.usecase.SearchChannelsUseCase
import kg.dev.shared.feature.search.presentation.DefaultSearchComponent
import kg.dev.shared.feature.search.presentation.SearchComponent
import org.koin.android.ext.android.get

class MainActivity : ComponentActivity() {
    private val rootComponent by lazy {
        lateinit var root: DefaultRootComponent<SearchComponent>
        root = DefaultRootComponent(defaultComponentContext()) { childContext ->
            DefaultSearchComponent(childContext, get<SearchChannelsUseCase>(), onMediaSelected = root::showPlayer)
        }
        root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyYoutubePlayerTheme {
                MainScreen(rootComponent = rootComponent)
            }
        }
    }
}
