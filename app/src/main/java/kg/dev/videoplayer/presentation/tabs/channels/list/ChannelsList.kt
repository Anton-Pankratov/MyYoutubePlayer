package kg.dev.videoplayer.presentation.tabs.channels.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import kg.dev.videoplayer.data.channel.ChannelViewData

@Composable
fun ChannelsList(
    channels: LazyPagingItems<ChannelViewData>,
    onItemClick: (ChannelViewData) -> Unit,
    afterLastItem: @Composable () -> Unit
) {

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(channels.itemCount) { index ->
            channels[index]?.let { channel ->
                ChannelItem(channel) {

                }
                if (index == channels.itemCount - 1) {
                    afterLastItem.invoke()
                }
            }
        }
    }
}