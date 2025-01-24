package kg.dev.videoplayer.presentation.main

import androidx.compose.foundation.layout.padding

import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import kg.dev.videoplayer.presentation.barBottom.BottomNavigationBar
import kg.dev.videoplayer.presentation.barBottom.NavigationHost
import kg.dev.videoplayer.presentation.barTop.TopBar
import org.koin.androidx.compose.koinViewModel

@Composable
fun MainScreen(mainViewModel: MainViewModel = koinViewModel()) {
    val navController = rememberNavController()

    Scaffold(
        topBar = { TopBar() },
        bottomBar = {
            BottomNavigationBar(navController) { selectedTab ->
                mainViewModel.updateSelectedTabState(selectedTab)
            }
        }
    ) { paddingValues ->
        NavigationHost(navController = navController, modifier = Modifier.padding(paddingValues))
    }
}
