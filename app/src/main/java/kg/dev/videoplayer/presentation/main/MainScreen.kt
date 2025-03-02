package kg.dev.videoplayer.presentation.main

import androidx.compose.foundation.layout.padding

import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import kg.dev.videoplayer.presentation.tabs.NavTab
import kg.dev.videoplayer.presentation.view.barBottom.BottomNavigationBar
import kg.dev.videoplayer.presentation.view.barBottom.NavigationHost
import kg.dev.videoplayer.presentation.view.barTop.TopBar
import org.koin.androidx.compose.koinViewModel

@Composable
fun MainScreen(mainViewModel: MainViewModel = koinViewModel()) {
    val navController = rememberNavController()

    var searchQuery by remember { mutableStateOf("") }
    var inSearching by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopBar(
                searchQuery = searchQuery,
                inSearching = inSearching,
                onSearchQueryChanged = { newQuery ->
                    searchQuery = newQuery
                },
                onSearchIconClick = {
                    inSearching = !inSearching
                }
            )
        },
        bottomBar = {
            BottomNavigationBar(navController) { selectedTab ->
                mainViewModel.updateSelectedTabState(selectedTab.name)
            }
        }
    ) { paddingValues ->
        NavigationHost(
            navController = navController,
            modifier = Modifier.padding(paddingValues),
            screensAttrs = hashMapOf(NavTab.CHANNELS.name to searchQuery)
        )
    }
}