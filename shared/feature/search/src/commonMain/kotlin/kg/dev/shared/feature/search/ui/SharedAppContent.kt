package kg.dev.shared.feature.search.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import kg.dev.shared.core.ui.navigation.Configuration
import kg.dev.shared.core.ui.navigation.PlayerComponent
import kg.dev.shared.core.ui.navigation.RootComponent
import kg.dev.shared.feature.search.presentation.SearchComponent
import kg.dev.shared.feature.history.presentation.HistoryComponent
import kg.dev.shared.feature.history.presentation.HistoryItemUiModel
import kg.dev.shared.feature.history.ui.HistoryContent
import kg.dev.shared.core.common.media.MediaCatalogItem

@Composable
fun SharedAppContent(
    rootComponent: RootComponent<SearchComponent>,
    historyComponentFactory: ((com.arkivanov.decompose.ComponentContext, (HistoryItemUiModel) -> Unit) -> HistoryComponent)? = null,
    playerContent: @Composable (PlayerComponent, Modifier) -> Unit = { player, modifier ->
        CenteredLabel(player.title ?: "Player", modifier)
    }
) {
    val stack by rootComponent.childStack.subscribeAsState()
    val activeConfiguration = stack.active.configuration

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = activeConfiguration == Configuration.Home,
                    onClick = rootComponent::showHome,
                    icon = { Text("Home") }
                )
                NavigationBarItem(
                    selected = activeConfiguration == Configuration.Search,
                    onClick = rootComponent::showSearch,
                    icon = { Text("Search") }
                )
                NavigationBarItem(
                    selected = activeConfiguration == Configuration.Profile,
                    onClick = rootComponent::showProfile,
                    icon = { Text("Profile") }
                )
            }
        }
    ) { padding ->
        val contentModifier = Modifier.fillMaxSize().padding(padding)
        when (val child = stack.active.instance) {
            is RootComponent.Child.Home -> {
                val history = historyComponentFactory?.let { factory -> remember(child.component) {
                    factory(child.component) { item ->
                        rootComponent.openMedia(MediaCatalogItem(item.reference, item.title, item.thumbnailUrl), item.positionMs)
                    }
                } }
                if (history == null) CenteredLabel("Home", contentModifier) else HistoryContent(history, contentModifier)
            }
            is RootComponent.Child.Search -> SearchContent(child.component, contentModifier)
            is RootComponent.Child.Player -> playerContent(child.component, contentModifier)
            is RootComponent.Child.Profile -> CenteredLabel("Profile", contentModifier)
        }
    }
}

@Composable
private fun CenteredLabel(text: String, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) { Text(text) }
}
