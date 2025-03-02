package kg.dev.videoplayer.presentation.view.barBottom

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import kg.dev.videoplayer.presentation.tabs.NavTab

@Composable
fun BottomNavigationBar(navController: NavHostController, selectedTab: (NavTab) -> Unit) {

    val currentDestination = navController.currentBackStackEntryAsState().value?.destination?.route

    NavigationBar {
        NavTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = currentDestination == tab.dest,
                icon = {
                    Icon(
                        imageVector = ImageVector.vectorResource(tab.iconRes),
                        contentDescription = stringResource(tab.textRes)
                    )
                },
                label = { Text(stringResource(tab.textRes)) },
                onClick = {
                    selectedTab.invoke(tab)
                    navController.navigate(tab.dest) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
    }
}