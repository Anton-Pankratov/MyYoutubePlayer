package kg.dev.videoplayer.presentation.tabs.channels

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun ChannelsScreen(viewModel: ChannelsViewModel = koinViewModel()) {
    val coroutineScope = rememberCoroutineScope()

    LazyColumn {
        coroutineScope.launch {
            viewModel.foundChannels.collect {
                println(it)
            }
        }
    }
}