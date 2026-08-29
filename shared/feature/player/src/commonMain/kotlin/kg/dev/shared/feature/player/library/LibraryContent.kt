package kg.dev.shared.feature.player.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import kg.dev.shared.core.ui.design.EmptyState
import kg.dev.shared.core.ui.design.MediaSpacing
import kg.dev.shared.core.ui.design.MediaTheme
import kg.dev.shared.core.ui.design.ErrorState

@Composable
fun LibraryContent(component: LibraryComponent, modifier: Modifier = Modifier) {
    val state by component.state.collectAsState()
    when (state) {
        LibraryUiState.Loading -> androidx.compose.foundation.layout.Box(modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator()
        }
        LibraryUiState.Error -> ErrorState("Library unavailable", "Saved media could not be loaded.", modifier)
        is LibraryUiState.Content -> {
            val content = state as LibraryUiState.Content
            Column(modifier.fillMaxSize().padding(MediaSpacing.lg), verticalArrangement = Arrangement.spacedBy(MediaSpacing.xl)) {
                SavedSection("Favorites", content.favorites, component::open, component::removeFavorite, "No favorites yet")
                SavedSection("Watch Later", content.watchLater, component::open, component::removeWatchLater, "Nothing in Watch Later")
            }
        }
    }
}

@Composable
private fun SavedSection(
    title: String,
    items: List<SavedMedia>,
    open: (SavedMedia) -> Unit,
    remove: (SavedMedia) -> Unit,
    empty: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(MediaSpacing.sm)) {
        Text(title, style = MediaTheme.typography.screenTitle, color = MediaTheme.colors.textPrimary)
        if (items.isEmpty()) EmptyState(title, empty)
        items.forEach { item ->
            Row(
                Modifier.fillMaxWidth().clickable { open(item) }.padding(vertical = MediaSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(MediaSpacing.md)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MediaTheme.typography.cardTitle, color = MediaTheme.colors.textPrimary)
                    item.authorTitle?.let { Text(it, style = MediaTheme.typography.metadata, color = MediaTheme.colors.textSecondary) }
                }
                OutlinedButton(onClick = { remove(item) }) { Text("Remove") }
            }
        }
    }
}
