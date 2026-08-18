package kg.dev.shared.feature.history.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kg.dev.shared.feature.history.presentation.HistoryItemUiModel
import kg.dev.shared.feature.history.presentation.HistoryUiState
import kg.dev.shared.feature.history.presentation.HistoryComponent

@Composable
fun HistoryContent(component: HistoryComponent, modifier: Modifier = Modifier) =
    HistoryContent(component.state.collectAsState().value, component::select, modifier)

@Composable
fun HistoryContent(state: HistoryUiState, onItemClick: (HistoryItemUiModel) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            state.isLoading -> CircularProgressIndicator()
            state.error -> Text("History could not be loaded.", color = MaterialTheme.colorScheme.error)
            state.items.isEmpty() -> Text("No playback history yet.")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.items, key = { "${it.reference.provider.value}:${it.reference.externalId}" }) { item ->
                    HistoryItem(item, { onItemClick(item) })
                }
            }
        }
    }
}

@Composable
fun HistoryItem(item: HistoryItemUiModel, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Text(item.title, style = MaterialTheme.typography.titleMedium)
            Text("${item.positionMs / 1000}s" + (item.durationMs?.let { " / ${it / 1000}s" } ?: ""), style = MaterialTheme.typography.bodySmall)
        }
    }
}
