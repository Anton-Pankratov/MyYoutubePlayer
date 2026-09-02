package kg.dev.shared.feature.player.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
fun LibraryContent(component: LibraryComponent, modifier: Modifier = Modifier, onAddToCollection: ((SavedMedia) -> Unit)? = null) {
    val state by component.state.collectAsState()
    when (state) {
        LibraryUiState.Loading -> androidx.compose.foundation.layout.Box(modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator()
        }
        LibraryUiState.Error -> ErrorState("Library unavailable", "Saved media could not be loaded.", modifier)
        is LibraryUiState.Content -> {
            val content = state as LibraryUiState.Content
            Column(modifier.fillMaxSize().padding(MediaSpacing.lg), verticalArrangement = Arrangement.spacedBy(MediaSpacing.xl)) {
                OutlinedTextField(content.searchQuery, component::onSearchQueryChanged, Modifier.fillMaxWidth(), label = { Text("Search saved media") })
                Row(horizontalArrangement = Arrangement.spacedBy(MediaSpacing.sm)) {
                    SavedMediaFilter.entries.forEach { filter -> OutlinedButton(onClick = { component.onFilterSelected(filter) }) { Text(filter.label()) } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(MediaSpacing.sm)) {
                    SavedMediaSort.entries.forEach { sort -> OutlinedButton(onClick = { component.onSortSelected(sort) }) { Text(sort.label()) } }
                }
                if (!content.hasAnySavedMedia) EmptyState("Library", "Nothing saved yet")
                else if (content.favorites.isEmpty() && content.watchLater.isEmpty()) EmptyState("No matches", "Try another search or filter")
                else {
                    if (content.showFavorites) SavedSection("Favorites", content.favorites, component::open, component::removeFavorite, "No favorites yet", onAddToCollection)
                    if (content.showWatchLater) SavedSection("Watch Later", content.watchLater, component::open, component::removeWatchLater, "Nothing in Watch Later", onAddToCollection)
                }
            }
        }
    }
}

private fun SavedMediaFilter.label() = when (this) {
    SavedMediaFilter.All -> "All"; SavedMediaFilter.Favorites -> "Favorites"; SavedMediaFilter.WatchLater -> "Watch Later"; SavedMediaFilter.Both -> "Both"
}
private fun SavedMediaSort.label() = when (this) {
    SavedMediaSort.RecentlySaved -> "Recently Saved"; SavedMediaSort.TitleAscending -> "Title A–Z"; SavedMediaSort.TitleDescending -> "Title Z–A"
}

@Composable
private fun SavedSection(
    title: String,
    items: List<SavedMedia>,
    open: (SavedMedia) -> Unit,
    remove: (SavedMedia) -> Unit,
    empty: String,
    onAddToCollection: ((SavedMedia) -> Unit)?
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
                if (onAddToCollection != null) OutlinedButton(onClick = { onAddToCollection(item) }) { Text("Add to collection") }
            }
        }
    }
}
