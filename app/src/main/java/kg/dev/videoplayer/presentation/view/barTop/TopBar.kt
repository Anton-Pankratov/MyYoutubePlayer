package kg.dev.videoplayer.presentation.view.barTop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import kg.dev.videoplayer.presentation.main.MainViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun TopBar(
    searchQuery: String,
    inSearching: Boolean,
    onSearchQueryChanged: (String) -> Unit,
    onSearchIconClick: (String) -> Unit,
    mainViewModel: MainViewModel = koinViewModel()
) {
    val currentTab = mainViewModel.selectedTabState.collectAsState()

    SearchTopBar(
        currentTab = currentTab.value,
        searchQuery = searchQuery,
        inSearching = inSearching,
        onSearchQueryChanged = onSearchQueryChanged,
        onSearchIconClick = onSearchIconClick
    )
}