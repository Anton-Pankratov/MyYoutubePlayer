package kg.dev.videoplayer.presentation.tabs.channels.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kg.dev.shared.feature.search.domain.model.Channel

@Composable
fun ChannelsList(
    channels: List<Channel>,
    listState: LazyListState,
    onItemClick: (Channel) -> Unit,
    afterLastItem: @Composable () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(channels, key = { it.id }) { channel ->
            ChannelItem(channel) {
                onItemClick.invoke(it)
            }
        }
        item { afterLastItem.invoke() }
    }
}
