package kg.dev.videoplayer.presentation.tabs.channels

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import kg.dev.videoplayer.data.events.AppEvents
import kg.dev.videoplayer.presentation.tabs.channels.list.ChannelsList
import kg.dev.videoplayer.presentation.view.progress.Progress
import org.koin.androidx.compose.koinViewModel

@Composable
fun ChannelsScreen(searchQuery: String?, viewModel: ChannelsViewModel = koinViewModel()) {
    val channels = viewModel.channels.collectAsLazyPagingItems()
    val pageLoadingState = viewModel.pageLoad.collectAsState()
    val channelsIsLoading = channels.loadState.refresh is LoadState.Loading

    LaunchedEffect(searchQuery) {
        viewModel.findChannels(searchQuery)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (channelsIsLoading) {
            Progress.Large().View()
        } else {
            ChannelsList(
                channels = channels,
                onItemClick = {

                },
                afterLastItem = {
                    if (pageLoadingState.value == AppEvents.Channel.CallNextPage.Loading) {
                        Progress.Small().View()
                    }
                }
            )
        }
    }
}