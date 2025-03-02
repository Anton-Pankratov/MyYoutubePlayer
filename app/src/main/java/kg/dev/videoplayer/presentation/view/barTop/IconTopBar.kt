package kg.dev.videoplayer.presentation.view.barTop

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import kg.dev.videoplayer.R
import kg.dev.videoplayer.presentation.tabs.NavTab

@Composable
fun IconTopBar(tabName: String, onSearchIconClick: (String) -> Unit) {
    when (tabName) {
        NavTab.PROFILE.name -> {
            IconButton(onClick = {
                onSearchIconClick.invoke(tabName)
            }) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.settings)
                )
            }
        }

        NavTab.CHANNELS.name -> {
            IconButton(onClick = {
                onSearchIconClick.invoke(tabName)
            }) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.search)
                )
            }
        }
    }
}