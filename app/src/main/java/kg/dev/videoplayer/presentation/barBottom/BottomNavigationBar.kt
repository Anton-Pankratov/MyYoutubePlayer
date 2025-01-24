package kg.dev.videoplayer.presentation.barBottom

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import kg.dev.videoplayer.presentation.tabs.NavTab
import kg.dev.videoplayer.presentation.tabs.channels.ChannelsScreen
import kg.dev.videoplayer.presentation.tabs.home.HomeScreen
import kg.dev.videoplayer.presentation.tabs.profile.ProfileScreen

@Composable
fun BottomNavigationBar(navController: NavHostController, selectedTab: (String) -> Unit) {

    var currentTab by rememberSaveable { mutableStateOf(NavTab.HOME) }

    NavigationBar {
        NavTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = currentTab == tab,
                onClick = {
                    currentTab = tab
                    navController.navigate(tab.dest)
                    selectedTab.invoke(tab.name)
                },
                icon = {
                    Icon(
                        imageVector = ImageVector.vectorResource(tab.iconRes),
                        contentDescription = stringResource(tab.textRes)
                    )
                },
                label = { Text(stringResource(tab.textRes)) }
            )
        }
    }
}

@Composable
fun NavigationHost(navController: NavHostController, modifier: Modifier) {
    NavHost(
        navController = navController,
        startDestination = NavTab.HOME.dest,
        modifier = modifier
    ) {
        composable(NavTab.HOME.dest) { HomeScreen() }
        composable(NavTab.CHANNELS.dest) { ChannelsScreen() }
        composable(NavTab.PROFILE.dest) { ProfileScreen() }
    }
}