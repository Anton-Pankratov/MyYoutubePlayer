package kg.dev.shared.feature.player.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import kg.dev.shared.core.ui.design.LoadingMediaCard

@Composable
fun LibraryContent(component: LibraryComponent, modifier: Modifier = Modifier, onAddToCollection: ((SavedMedia) -> Unit)? = null) {
    val state by component.state.collectAsState()
    when (state) {
        LibraryUiState.Loading -> Column(
            modifier.fillMaxSize().padding(MediaSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(MediaSpacing.lg),
        ) {
            repeat(5) { LoadingMediaCard(compact = true) }
        }
        LibraryUiState.Error -> ErrorState("Library unavailable", "Saved media could not be loaded.", modifier)
        is LibraryUiState.Content -> {
            val content = state as LibraryUiState.Content
            LazyColumn(
                modifier.fillMaxSize().padding(MediaSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(MediaSpacing.lg),
            ) {
                item(key = "search") {
                    OutlinedTextField(
                        content.searchQuery,
                        component::onSearchQueryChanged,
                        Modifier.fillMaxWidth(),
                        label = { Text("Search saved media") },
                    )
                }
                item(key = "filters") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(MediaSpacing.sm)) {
                        items(SavedMediaFilter.entries) { filter ->
                            OutlinedButton(onClick = { component.onFilterSelected(filter) }) { Text(filter.label()) }
                        }
                    }
                }
                item(key = "sorts") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(MediaSpacing.sm)) {
                        items(SavedMediaSort.entries) { sort ->
                            OutlinedButton(onClick = { component.onSortSelected(sort) }) { Text(sort.label()) }
                        }
                    }
                }
                when {
                    !content.hasAnySavedMedia -> item { EmptyState("Library", "Nothing saved yet") }
                    content.favorites.isEmpty() && content.watchLater.isEmpty() ->
                        item { EmptyState("No matches", "Try another search or filter") }
                    else -> {
                        if (content.showFavorites) savedSection(
                            "Favorites", content.favorites, component::open, component::removeFavorite,
                            "No favorites yet", onAddToCollection,
                        )
                        if (content.showWatchLater) savedSection(
                            "Watch Later", content.watchLater, component::open, component::removeWatchLater,
                            "Nothing in Watch Later", onAddToCollection,
                        )
                    }
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

private fun LazyListScope.savedSection(
    title: String,
    items: List<SavedMedia>,
    open: (SavedMedia) -> Unit,
    remove: (SavedMedia) -> Unit,
    empty: String,
    onAddToCollection: ((SavedMedia) -> Unit)?
) {
    item(key = "heading:$title") {
        Text(title, style = MediaTheme.typography.screenTitle, color = MediaTheme.colors.textPrimary)
    }
    if (items.isEmpty()) item(key = "empty:$title") { EmptyState(title, empty) }
    else items(
        items,
        key = { "$title:${it.reference.provider.value}:${it.reference.externalId}" },
    ) { item ->
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
