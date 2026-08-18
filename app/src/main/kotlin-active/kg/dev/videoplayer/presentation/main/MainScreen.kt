package kg.dev.videoplayer.presentation.main

import androidx.compose.runtime.Composable
import kg.dev.shared.core.ui.navigation.RootComponent
import kg.dev.shared.feature.search.presentation.SearchComponent
import kg.dev.shared.feature.search.ui.SharedAppContent
import kg.dev.shared.feature.player.presentation.PlayerComponent
import kg.dev.shared.feature.player.ui.AndroidPlayerContent
import kg.dev.shared.feature.history.presentation.DefaultHistoryComponent
import kg.dev.shared.feature.history.domain.HistoryRepository
import org.koin.compose.koinInject

@Composable
fun MainScreen(rootComponent: RootComponent<SearchComponent>) {
    val historyRepository = koinInject<HistoryRepository>()
    SharedAppContent(rootComponent, historyComponentFactory = { context, selected ->
        DefaultHistoryComponent(context, historyRepository, onItemSelected = selected)
    }) { navigationComponent, modifier ->
        val playerComponent = navigationComponent as? PlayerComponent
        if (playerComponent is kg.dev.shared.feature.player.presentation.DefaultPlayerComponent) {
            AndroidPlayerContent(playerComponent, modifier)
        }
    }
}
