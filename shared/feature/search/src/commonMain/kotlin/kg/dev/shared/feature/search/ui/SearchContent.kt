package kg.dev.shared.feature.search.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kg.dev.shared.feature.search.presentation.SearchComponent

@Composable
fun SearchContent(component: SearchComponent, modifier: Modifier = Modifier) {
    val state by component.state.collectAsState()
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextField(
            value = state.query,
            onValueChange = component::onQueryChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(if (state.selectedChannel == null) "Search channels" else "Selected channel") }
        )
        if (state.isLoading) {
            CircularProgressIndicator()
        } else if (state.error != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Search failed: ${state.error}", color = MaterialTheme.colorScheme.error)
                Button(onClick = component::retry) { Text("Retry") }
            }
        } else {
            if (state.selectedChannel != null) {
                Button(onClick = component::showChannels) { Text("Back to channels") }
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.selectedChannel == null) items(state.items, key = { "${it.providerId.value}:${it.id}" }) { channel ->
                    Button(onClick = { component.selectChannel(channel) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(channel.title, style = MaterialTheme.typography.titleMedium)
                            Text(channel.description, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                else items(state.videos, key = { "${it.reference.provider.value}:${it.reference.externalId}" }) { video ->
                    Button(onClick = { component.selectVideo(video) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(video.title, style = MaterialTheme.typography.titleMedium)
                            video.authorTitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
            if (state.canLoadMore) {
                Button(onClick = component::loadNextPage) { Text("Load more") }
            } else if (state.isLoadingMore) {
                CircularProgressIndicator()
            }
        }
    }
}
