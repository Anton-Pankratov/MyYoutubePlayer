package kg.dev.videoplayer.presentation.view.barBottom

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import kg.dev.videoplayer.presentation.tabs.NavTab
import kg.dev.videoplayer.presentation.tabs.channels.ChannelsScreen
import kg.dev.videoplayer.presentation.tabs.channels.ChannelsViewModel
import kg.dev.videoplayer.presentation.tabs.home.HomeScreen
import kg.dev.videoplayer.presentation.tabs.profile.ProfileScreen

@Composable
fun NavigationHost(
    navController: NavHostController,
    saveableStateHolder: SaveableStateHolder,
    channelsViewModel: ChannelsViewModel,
    modifier: Modifier
) {
    NavHost(
        navController = navController,
        startDestination = NavTab.HOME.dest,
        modifier = modifier
    ) {
        composable(NavTab.HOME.dest) {
            saveableStateHolder.SaveableStateProvider(NavTab.HOME.dest) {
                HomeScreen()
            }
        }
        composable(NavTab.CHANNELS.dest) {
            saveableStateHolder.SaveableStateProvider(NavTab.CHANNELS.dest) {
                ChannelsScreen(channelsViewModel)
            }
        }
        composable(NavTab.PROFILE.dest) {
            saveableStateHolder.SaveableStateProvider(NavTab.PROFILE.dest) {
                ProfileScreen()
            }
        }
    }
}
