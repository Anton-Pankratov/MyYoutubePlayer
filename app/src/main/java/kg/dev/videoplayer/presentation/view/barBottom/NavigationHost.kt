package kg.dev.videoplayer.presentation.view.barBottom

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import kg.dev.common.utils.safeCast
import kg.dev.videoplayer.presentation.tabs.NavTab
import kg.dev.videoplayer.presentation.tabs.channels.ChannelsScreen
import kg.dev.videoplayer.presentation.tabs.home.HomeScreen
import kg.dev.videoplayer.presentation.tabs.profile.ProfileScreen

@Composable
fun NavigationHost(navController: NavHostController, modifier: Modifier, screensAttrs: Map<String, Any>) {
    val channelsSearchQuery = screensAttrs[NavTab.CHANNELS.name]

    NavHost(
        navController = navController,
        startDestination = NavTab.HOME.dest,
        modifier = modifier
    ) {
        composable(NavTab.HOME.dest) { HomeScreen() }
        composable(NavTab.CHANNELS.dest) { ChannelsScreen(channelsSearchQuery.safeCast()) }
        composable(NavTab.PROFILE.dest) { ProfileScreen() }
    }
}