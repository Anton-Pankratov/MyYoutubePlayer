package kg.dev.videoplayer.presentation.tabs.channels

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import kg.dev.videoplayer.presentation.tabs.channels.list.ChannelsList
import kg.dev.videoplayer.presentation.view.error.ErrorScreen
import kg.dev.videoplayer.presentation.view.progress.Progress
import org.koin.compose.koinInject
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun ChannelsScreen(viewModel: ChannelsViewModel = koinInject()) {
    val state by viewModel.state.collectAsState()
    val scrollState = rememberLazyListState()

    LaunchedEffect(scrollState, state.items.size) {
        snapshotFlow { scrollState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex != null && lastVisibleIndex >= state.items.lastIndex) {
                    viewModel.loadNextPage()
                }
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (state.isLoading) {
            Progress.Large().View()
        } else if (state.error != null && state.items.isEmpty()) {
            ErrorScreen(onRetry = viewModel::retry)
        } else {
            ChannelsList(
                channels = state.items,
                listState = scrollState,
                onItemClick = {

                },
                afterLastItem = {
                    if (state.isLoadingMore) Progress.Small().View()
                }
            )
        }
    }
}
