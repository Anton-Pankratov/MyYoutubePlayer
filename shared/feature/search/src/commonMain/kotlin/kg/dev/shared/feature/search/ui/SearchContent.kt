package kg.dev.shared.feature.search.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kg.dev.shared.core.common.media.MediaCatalogItem
import kg.dev.shared.core.ui.design.AdaptiveLayout
import kg.dev.shared.core.ui.design.CompactProgress
import kg.dev.shared.core.ui.design.EmptyState
import kg.dev.shared.core.ui.design.ErrorState
import kg.dev.shared.core.ui.design.LoadingMediaCard
import kg.dev.shared.core.ui.design.MediaSearchField
import kg.dev.shared.core.ui.design.MediaShapes
import kg.dev.shared.core.ui.design.MediaSpacing
import kg.dev.shared.core.ui.design.MediaTheme
import kg.dev.shared.core.ui.design.MediaThumbnail
import kg.dev.shared.core.ui.design.MetadataText
import kg.dev.shared.core.ui.design.PrimaryAction
import kg.dev.shared.core.ui.design.ProviderBadge
import kg.dev.shared.core.ui.design.ScreenHeader
import kg.dev.shared.core.ui.design.layoutForWidth
import kg.dev.shared.feature.search.domain.model.Channel
import kg.dev.shared.feature.search.domain.model.SearchError
import kg.dev.shared.feature.search.presentation.SearchComponent
import kg.dev.shared.feature.search.presentation.SearchUiState

@Composable
fun SearchContent(component: SearchComponent, modifier: Modifier = Modifier) {
    val state by component.state.collectAsState()
    SearchContent(
        state = state,
        onQueryChanged = component::onQueryChanged,
        onChannelClick = component::selectChannel,
        onVideoClick = component::selectVideo,
        onBackToChannels = component::showChannels,
        onLoadMore = component::loadNextPage,
        onRetry = component::retry,
        modifier = modifier
    )
}

@Composable
fun SearchContent(
    state: SearchUiState,
    onQueryChanged: (String) -> Unit,
    onChannelClick: (Channel) -> Unit,
    onVideoClick: (MediaCatalogItem) -> Unit,
    onBackToChannels: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val layout = layoutForWidth(maxWidth)
        val horizontalPadding = if (layout == AdaptiveLayout.Compact) MediaSpacing.md else MediaSpacing.xxl
        Column(
            Modifier.fillMaxSize().widthIn(max = 1_240.dp).align(Alignment.TopCenter)
                .padding(horizontal = horizontalPadding, vertical = MediaSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(MediaSpacing.xl)
        ) {
            if (state.selectedChannel == null) {
                ScreenHeader(
                    title = "Discover",
                    supportingText = "Find channels and explore their latest videos."
                )
                MediaSearchField(
                    value = state.query,
                    onValueChange = onQueryChanged,
                    placeholder = "Search channels"
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBackToChannels) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to channels", tint = MediaTheme.colors.textPrimary)
                    }
                    ScreenHeader(
                        title = state.selectedChannel.title,
                        supportingText = "Latest videos",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.isLoading -> SearchLoading(layout)
                    state.error != null -> SearchErrorState(state.error, onRetry)
                    state.selectedChannel == null && state.items.isEmpty() -> EmptyState(
                        title = if (state.query.isBlank()) "Start exploring" else "No channels found",
                        message = if (state.query.isBlank()) {
                            "Search by topic, creator, or channel name."
                        } else {
                            "Try a broader search or check the spelling."
                        }
                    )
                    state.selectedChannel != null && state.videos.isEmpty() -> EmptyState(
                        title = "No videos yet",
                        message = "This channel doesn’t have any public videos to show."
                    )
                    state.selectedChannel == null -> ChannelResults(state, layout, onChannelClick, onLoadMore)
                    else -> VideoResults(state, layout, onVideoClick, onLoadMore)
                }
            }
        }
    }
}

@Composable
private fun SearchLoading(layout: AdaptiveLayout) {
    if (layout == AdaptiveLayout.Compact) {
        Column(verticalArrangement = Arrangement.spacedBy(MediaSpacing.lg)) {
            repeat(5) { LoadingMediaCard(compact = true) }
        }
    } else {
        LazyVerticalGrid(
            modifier = Modifier.fillMaxSize(),
            columns = GridCells.Adaptive(260.dp),
            horizontalArrangement = Arrangement.spacedBy(MediaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MediaSpacing.xl)
        ) { items(8) { LoadingMediaCard() } }
    }
}

@Composable
private fun SearchErrorState(error: SearchError, onRetry: () -> Unit) {
    val message = when (error) {
        SearchError.Network -> "Check your connection and try again."
        SearchError.Unauthorized -> "The media catalog is not configured for this application."
        SearchError.QuotaExceeded -> "The catalog is temporarily busy. Please try again later."
        SearchError.InvalidResponse -> "The catalog returned content we couldn’t read."
        SearchError.Unknown -> "Something unexpected happened while loading the catalog."
    }
    ErrorState("Couldn’t load results", message, onRetry = onRetry)
}

@Composable
private fun ChannelResults(
    state: SearchUiState,
    layout: AdaptiveLayout,
    onClick: (Channel) -> Unit,
    onLoadMore: () -> Unit
) {
    if (layout == AdaptiveLayout.Compact) {
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(MediaSpacing.xs)) {
            items(state.items, key = { "${it.providerId.value}:${it.id}" }) { channel ->
                ChannelCard(channel, onClick, compact = true)
            }
            item { PaginationFooter(state, onLoadMore) }
        }
    } else {
        LazyVerticalGrid(
            modifier = Modifier.fillMaxSize(),
            columns = GridCells.Adaptive(300.dp),
            horizontalArrangement = Arrangement.spacedBy(MediaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MediaSpacing.md),
            contentPadding = PaddingValues(bottom = MediaSpacing.xl)
        ) {
            items(state.items, key = { "${it.providerId.value}:${it.id}" }) { channel ->
                ChannelCard(channel, onClick, compact = false)
            }
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                PaginationFooter(state, onLoadMore)
            }
        }
    }
}

@Composable
private fun ChannelCard(channel: Channel, onClick: (Channel) -> Unit, compact: Boolean) {
    Surface(
        onClick = { onClick(channel) },
        color = MediaTheme.colors.surface,
        shape = MediaShapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(if (compact) MediaSpacing.sm else MediaSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(MediaSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MediaThumbnail(
                url = channel.thumbnailUrl,
                contentDescription = "${channel.title} channel image",
                circular = true,
                modifier = Modifier.size(if (compact) 64.dp else 76.dp)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MediaSpacing.xs)) {
                Text(
                    channel.title,
                    style = MediaTheme.typography.cardTitle,
                    color = MediaTheme.colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (channel.description.isNotBlank()) {
                    Text(
                        channel.description,
                        style = MediaTheme.typography.secondaryBody,
                        color = MediaTheme.colors.textSecondary,
                        maxLines = if (compact) 2 else 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                MetadataText("View latest videos")
            }
        }
    }
}

@Composable
private fun VideoResults(
    state: SearchUiState,
    layout: AdaptiveLayout,
    onClick: (MediaCatalogItem) -> Unit,
    onLoadMore: () -> Unit
) {
    if (layout == AdaptiveLayout.Compact) {
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(MediaSpacing.md)) {
            items(state.videos, key = { "${it.reference.provider.value}:${it.reference.externalId}" }) { video ->
                VideoCard(video, onClick, compact = true)
            }
            item { PaginationFooter(state, onLoadMore) }
        }
    } else {
        LazyVerticalGrid(
            modifier = Modifier.fillMaxSize(),
            columns = GridCells.Adaptive(260.dp),
            horizontalArrangement = Arrangement.spacedBy(MediaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MediaSpacing.xl),
            contentPadding = PaddingValues(bottom = MediaSpacing.xl)
        ) {
            items(state.videos, key = { "${it.reference.provider.value}:${it.reference.externalId}" }) { video ->
                VideoCard(video, onClick, compact = false)
            }
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                PaginationFooter(state, onLoadMore)
            }
        }
    }
}

@Composable
private fun VideoCard(video: MediaCatalogItem, onClick: (MediaCatalogItem) -> Unit, compact: Boolean) {
    Surface(
        onClick = { onClick(video) },
        color = MediaTheme.colors.surface,
        shape = MediaShapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (compact) {
            Row(Modifier.padding(MediaSpacing.xs), horizontalArrangement = Arrangement.spacedBy(MediaSpacing.sm)) {
                MediaThumbnail(video.thumbnailUrl, "Thumbnail for ${video.title}", Modifier.size(140.dp, 79.dp))
                VideoCardText(video, Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(MediaSpacing.sm)) {
                MediaThumbnail(video.thumbnailUrl, "Thumbnail for ${video.title}", Modifier.fillMaxWidth())
                VideoCardText(video, Modifier.padding(horizontal = MediaSpacing.sm).padding(bottom = MediaSpacing.sm))
            }
        }
    }
}

@Composable
private fun VideoCardText(video: MediaCatalogItem, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(MediaSpacing.xs)) {
        Text(
            video.title,
            style = MediaTheme.typography.cardTitle,
            color = MediaTheme.colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        video.authorTitle?.let { MetadataText(it) }
        ProviderBadge(video.reference.provider.value)
    }
}

@Composable
private fun PaginationFooter(state: SearchUiState, onLoadMore: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = MediaSpacing.lg), contentAlignment = Alignment.Center) {
        when {
            state.isLoadingMore -> CompactProgress()
            state.canLoadMore -> PrimaryAction("Load more", onLoadMore, leading = {
                Icon(Icons.Outlined.Explore, null, Modifier.size(18.dp))
            })
        }
    }
}
