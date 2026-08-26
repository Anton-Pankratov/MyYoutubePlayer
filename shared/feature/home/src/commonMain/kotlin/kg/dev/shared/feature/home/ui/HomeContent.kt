package kg.dev.shared.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kg.dev.shared.core.ui.design.EmptyState
import kg.dev.shared.core.ui.design.ErrorState
import kg.dev.shared.core.ui.design.LoadingMediaCard
import kg.dev.shared.core.ui.design.MediaShapes
import kg.dev.shared.core.ui.design.MediaSpacing
import kg.dev.shared.core.ui.design.MediaTheme
import kg.dev.shared.core.ui.design.MediaThumbnail
import kg.dev.shared.core.ui.design.MetadataText
import kg.dev.shared.core.ui.design.ScreenHeader
import kg.dev.shared.feature.history.domain.ResumeDecision
import kg.dev.shared.feature.home.presentation.HomeComponent
import kg.dev.shared.feature.home.presentation.HomeMediaItemUiModel
import kg.dev.shared.feature.home.presentation.HomeUiState

@Composable
fun HomeContent(component: HomeComponent, modifier: Modifier = Modifier) =
    HomeContent(component.state.collectAsState().value, component::select, component::refresh, modifier)

@Composable
fun HomeContent(
    state: HomeUiState,
    onItemClick: (HomeMediaItemUiModel) -> Unit,
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().widthIn(max = 1_240.dp).align(Alignment.TopCenter)
                .padding(horizontal = MediaSpacing.md, vertical = MediaSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(MediaSpacing.xxl)
        ) {
            ScreenHeader("Home", "Pick up where you left off or revisit a recent watch.")
            when {
                state.isLoading -> HomeLoading()
                state.error -> ErrorState(
                    "Home is unavailable",
                    "We couldn’t load your recent playback activity.",
                    onRetry = onRetry
                )
                state.recentlyWatched.isEmpty() -> EmptyState(
                    "No videos watched yet",
                    "Start playing a video and it will appear here for easy access."
                )
                else -> {
                    if (state.continueWatching.isNotEmpty()) {
                        HomeSection("Continue watching", state.continueWatching, onItemClick, showProgress = true)
                    }
                    HomeSection("Recently watched", state.recentlyWatched, onItemClick, showProgress = false)
                }
            }
        }
    }
}

@Composable
private fun HomeLoading() {
    Column(verticalArrangement = Arrangement.spacedBy(MediaSpacing.xxl)) {
        repeat(2) {
            Column(verticalArrangement = Arrangement.spacedBy(MediaSpacing.md)) {
                Box(Modifier.width(180.dp).height(22.dp).background(MediaTheme.colors.surfaceElevated, MediaShapes.small))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(MediaSpacing.md)) {
                    items(3) { LoadingMediaCard(Modifier.width(264.dp)) }
                }
            }
        }
    }
}

@Composable
private fun HomeSection(
    title: String,
    items: List<HomeMediaItemUiModel>,
    onItemClick: (HomeMediaItemUiModel) -> Unit,
    showProgress: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(MediaSpacing.md)) {
        Text(title, style = MediaTheme.typography.sectionTitle, color = MediaTheme.colors.textPrimary)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(MediaSpacing.md)) {
            items(items, key = { "${it.reference.provider.value}:${it.reference.externalId}" }) { item ->
                HomeMediaCard(item, onItemClick, showProgress)
            }
        }
    }
}

@Composable
private fun HomeMediaCard(
    item: HomeMediaItemUiModel,
    onItemClick: (HomeMediaItemUiModel) -> Unit,
    showProgress: Boolean
) {
    Surface(
        onClick = { onItemClick(item) },
        enabled = item.isAvailable,
        color = MediaTheme.colors.surface,
        shape = MediaShapes.medium,
        modifier = Modifier.width(264.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MediaSpacing.sm)) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                MediaThumbnail(item.thumbnailUrl, "Thumbnail for ${item.title}", Modifier.fillMaxSize())
                Surface(
                    color = MediaTheme.colors.overlay,
                    shape = androidx.compose.foundation.shape.CircleShape,
                    modifier = Modifier.align(Alignment.Center).size(40.dp)
                ) {
                    Icon(
                        Icons.Outlined.PlayArrow,
                        if (item.resumeDecision == ResumeDecision.Completed) "Play again" else "Play",
                        tint = MediaTheme.colors.playerControls,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                if (showProgress) HomeProgress(item, Modifier.align(Alignment.BottomCenter))
            }
            Column(
                Modifier.padding(horizontal = MediaSpacing.sm).padding(bottom = MediaSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(MediaSpacing.xs)
            ) {
                Text(
                    item.title,
                    style = MediaTheme.typography.cardTitle,
                    color = if (item.isAvailable) MediaTheme.colors.textPrimary else MediaTheme.colors.textTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                MetadataText(homeMetadata(item))
            }
        }
    }
}

@Composable
private fun HomeProgress(item: HomeMediaItemUiModel, modifier: Modifier = Modifier) {
    val duration = item.durationMs?.takeIf { it > 0 }
    val progress = if (duration == null) 0f else (item.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    Box(modifier.fillMaxWidth().height(3.dp).background(MediaTheme.colors.surfaceInteractive)) {
        Box(Modifier.fillMaxWidth(progress).height(3.dp).background(MediaTheme.colors.primary))
    }
}

private fun homeMetadata(item: HomeMediaItemUiModel): String = when {
    !item.isAvailable -> "This media is not available on this device"
    item.resumeDecision == ResumeDecision.Completed -> "Watched"
    item.resumeDecision is ResumeDecision.ResumeFrom -> item.durationMs?.let { duration ->
        "${formatTime(item.positionMs)} of ${formatTime(duration)}"
    } ?: "Continue watching"
    else -> "Not started"
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0) / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    else "$minutes:${seconds.toString().padStart(2, '0')}"
}
