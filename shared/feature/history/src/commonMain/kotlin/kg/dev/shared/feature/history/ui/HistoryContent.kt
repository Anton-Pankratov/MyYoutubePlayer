package kg.dev.shared.feature.history.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kg.dev.shared.core.common.media.MediaProviders
import kg.dev.shared.core.ui.design.AdaptiveLayout
import kg.dev.shared.core.ui.design.EmptyState
import kg.dev.shared.core.ui.design.ErrorState
import kg.dev.shared.core.ui.design.LoadingMediaCard
import kg.dev.shared.core.ui.design.MediaShapes
import kg.dev.shared.core.ui.design.MediaSpacing
import kg.dev.shared.core.ui.design.MediaTheme
import kg.dev.shared.core.ui.design.MediaThumbnail
import kg.dev.shared.core.ui.design.MetadataText
import kg.dev.shared.core.ui.design.ProviderBadge
import kg.dev.shared.core.ui.design.ScreenHeader
import kg.dev.shared.core.ui.design.layoutForWidth
import kg.dev.shared.feature.history.presentation.HistoryComponent
import kg.dev.shared.feature.history.presentation.HistoryItemUiModel
import kg.dev.shared.feature.history.presentation.HistoryUiState
import kg.dev.shared.feature.history.domain.ResumeDecision

@Composable
fun HistoryContent(component: HistoryComponent, modifier: Modifier = Modifier) =
    HistoryContent(component.state.collectAsState().value, component::select, component::refresh, modifier)

@Composable
fun HistoryContent(
    state: HistoryUiState,
    onItemClick: (HistoryItemUiModel) -> Unit,
    onRetry: () -> Unit = {},
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
            ScreenHeader(
                title = "Watch history",
                supportingText = "Continue where you left off or revisit something memorable."
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.isLoading -> HistoryLoading(layout)
                    state.error -> ErrorState(
                        "History is unavailable",
                        "We couldn’t load your recent playback activity.",
                        onRetry = onRetry
                    )
                    state.items.isEmpty() -> EmptyState(
                        title = "Nothing watched yet",
                        message = "Videos you start playing will appear here for easy access."
                    )
                    layout == AdaptiveLayout.Compact -> LazyColumn(
                        Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(MediaSpacing.sm)
                    ) {
                        items(state.items, key = { "${it.reference.provider.value}:${it.reference.externalId}" }) { item ->
                            HistoryCard(item, onItemClick, compact = true)
                        }
                    }
                    else -> LazyVerticalGrid(
                        modifier = Modifier.fillMaxSize(),
                        columns = GridCells.Adaptive(270.dp),
                        horizontalArrangement = Arrangement.spacedBy(MediaSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(MediaSpacing.xl),
                        contentPadding = PaddingValues(bottom = MediaSpacing.xl)
                    ) {
                        items(state.items, key = { "${it.reference.provider.value}:${it.reference.externalId}" }) { item ->
                            HistoryCard(item, onItemClick, compact = false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryLoading(layout: AdaptiveLayout) {
    if (layout == AdaptiveLayout.Compact) {
        Column(verticalArrangement = Arrangement.spacedBy(MediaSpacing.lg)) {
            repeat(5) { LoadingMediaCard(compact = true) }
        }
    } else {
        LazyVerticalGrid(
            modifier = Modifier.fillMaxSize(),
            columns = GridCells.Adaptive(270.dp),
            horizontalArrangement = Arrangement.spacedBy(MediaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MediaSpacing.xl)
        ) { items(8) { LoadingMediaCard() } }
    }
}

@Composable
private fun HistoryCard(
    item: HistoryItemUiModel,
    onClick: (HistoryItemUiModel) -> Unit,
    compact: Boolean
) {
    val isKnownProvider = item.reference.provider == MediaProviders.YouTube || item.reference.provider == MediaProviders.Direct
    Surface(
        onClick = { if (isKnownProvider) onClick(item) },
        enabled = isKnownProvider,
        color = MediaTheme.colors.surface,
        shape = MediaShapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (compact) {
            Row(Modifier.padding(MediaSpacing.xs), horizontalArrangement = Arrangement.spacedBy(MediaSpacing.sm)) {
                ThumbnailWithProgress(item, Modifier.size(144.dp, 81.dp))
                HistoryDetails(item, isKnownProvider, Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(MediaSpacing.sm)) {
                ThumbnailWithProgress(item, Modifier.fillMaxWidth())
                HistoryDetails(
                    item,
                    isKnownProvider,
                    Modifier.padding(horizontal = MediaSpacing.sm).padding(bottom = MediaSpacing.sm)
                )
            }
        }
    }
}

@Composable
private fun ThumbnailWithProgress(item: HistoryItemUiModel, modifier: Modifier) {
    Column(modifier.aspectRatio(16f / 9f)) {
        Box(Modifier.weight(1f)) {
            MediaThumbnail(item.thumbnailUrl, "Thumbnail for ${item.title}", Modifier.fillMaxSize())
            Surface(
                color = MediaTheme.colors.overlay,
                shape = androidx.compose.foundation.shape.CircleShape,
                modifier = Modifier.align(Alignment.Center).size(36.dp)
            ) {
                Icon(
                    Icons.Outlined.PlayArrow,
                    if (item.resumeDecision == ResumeDecision.Completed) "Play again" else "Resume playback",
                    tint = MediaTheme.colors.playerControls,
                    modifier = Modifier.padding(7.dp)
                )
            }
        }
        PlaybackProgress(item)
    }
}

@Composable
private fun PlaybackProgress(item: HistoryItemUiModel) {
    val duration = item.durationMs?.takeIf { it > 0 }
    val progress = when {
        item.resumeDecision == ResumeDecision.Completed -> 1f
        duration == null -> 0f
        else -> (item.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    }
    Box(Modifier.fillMaxWidth().height(3.dp).background(MediaTheme.colors.surfaceInteractive)) {
        Box(Modifier.fillMaxWidth(progress).height(3.dp).background(MediaTheme.colors.primary))
    }
}

@Composable
private fun HistoryDetails(item: HistoryItemUiModel, isKnownProvider: Boolean, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(MediaSpacing.xs)) {
        Text(
            item.title,
            style = MediaTheme.typography.cardTitle,
            color = if (isKnownProvider) MediaTheme.colors.textPrimary else MediaTheme.colors.textTertiary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        MetadataText(
            if (isKnownProvider) {
                if (item.resumeDecision == ResumeDecision.Completed) {
                    "Watched"
                } else {
                    "${formatTime(item.positionMs)}${item.durationMs?.let { " of ${formatTime(it)}" }.orEmpty()}"
                }
            } else {
                "This archived source is no longer available"
            }
        )
        ProviderBadge(item.reference.provider.value)
    }
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0) / 1_000)
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    else "$minutes:${seconds.toString().padStart(2, '0')}"
}
