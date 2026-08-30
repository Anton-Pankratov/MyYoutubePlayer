package kg.dev.videoplayer.presentation.main

import androidx.compose.runtime.Composable
import kg.dev.shared.core.ui.navigation.RootComponent
import kg.dev.shared.feature.search.presentation.SearchComponent
import kg.dev.shared.feature.search.ui.SharedAppContent

@Composable
fun MainScreen(rootComponent: RootComponent<SearchComponent>) {
    SharedAppContent(rootComponent)
}
