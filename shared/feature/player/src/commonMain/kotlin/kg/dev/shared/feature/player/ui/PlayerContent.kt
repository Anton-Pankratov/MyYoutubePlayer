package kg.dev.shared.feature.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kg.dev.shared.core.ui.design.ErrorState
import kg.dev.shared.core.ui.design.MediaShapes
import kg.dev.shared.core.ui.design.MediaSpacing
import kg.dev.shared.core.ui.design.MediaTheme
import kg.dev.shared.core.ui.design.MediaThumbnail
import kg.dev.shared.core.ui.design.MetadataText
import kg.dev.shared.core.ui.design.ProviderBadge
import kg.dev.shared.core.common.media.MediaCatalogItem
import kg.dev.shared.core.common.media.MediaProviderId
import kg.dev.shared.core.common.media.MediaReference
import kg.dev.shared.feature.player.ProviderPlaybackAdapterRegistry
import kg.dev.shared.feature.player.ProviderPlaybackSession
import kg.dev.shared.feature.player.PlaybackSource
import kg.dev.shared.feature.player.PlaybackState
import kg.dev.shared.feature.player.PlayableMedia
import kg.dev.shared.feature.player.PlayerError
import kg.dev.shared.feature.player.presentation.PlayerComponent
import kg.dev.shared.feature.player.presentation.PlayerUiState
import kg.dev.shared.core.ui.navigation.PlayerComponent as NavigationPlayerComponent

@Composable
fun PlayerContent(
    component: PlayerComponent,
    modifier: Modifier = Modifier,
    mediaSurface: @Composable ((Modifier) -> Unit)? = null,
    providerAdapters: ProviderPlaybackAdapterRegistry = ProviderPlaybackAdapterRegistry.Empty
) {
    val state by component.state.collectAsState()
    PlayerContent(
        state = state,
        onPlay = component::play,
        onPause = component::pause,
        onSeek = component::seekTo,
        onRetry = component::retry,
        onSetFavorite = component::setFavorite,
        onSetWatchLater = component::setWatchLater,
        modifier = modifier,
        mediaSurface = mediaSurface,
        providerAdapters = providerAdapters,
        providerSession = component.providerPlaybackSession
    )
}

@Composable
fun ProviderPlayerContent(
    component: NavigationPlayerComponent,
    modifier: Modifier = Modifier,
    providerAdapters: ProviderPlaybackAdapterRegistry = ProviderPlaybackAdapterRegistry.Empty
) {
    val reference = MediaReference(MediaProviderId(component.providerId), component.mediaId)
    val source = if (component.playbackKind == "direct") {
        PlaybackSource.Direct(component.directUri.orEmpty(), component.mimeType)
    } else {
        PlaybackSource.ProviderControlled(reference)
    }
    val media = PlayableMedia(
        catalogItem = MediaCatalogItem(
            reference = reference,
            title = component.title ?: component.mediaId,
            thumbnailUrl = component.thumbnailUrl,
            authorTitle = component.authorTitle,
            durationMs = component.catalogDurationMs
        ),
        source = source
    )
    val state = PlayerUiState(
        media = media,
        positionMs = component.startPositionMs
    )
    PlayerContent(
        state = state,
        onPlay = {},
        onPause = {},
        onSeek = {},
        onRetry = {},
        onSetFavorite = {},
        onSetWatchLater = {},
        modifier = modifier,
        providerAdapters = providerAdapters
    )
}

@Composable
fun PlayerContent(
    state: PlayerUiState,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onRetry: () -> Unit,
    onSetFavorite: (Boolean) -> Unit = {},
    onSetWatchLater: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    mediaSurface: @Composable ((Modifier) -> Unit)? = null,
    providerAdapters: ProviderPlaybackAdapterRegistry = ProviderPlaybackAdapterRegistry.Empty,
    providerSession: ProviderPlaybackSession? = null
) {
    val media = state.media
    val source = media?.source
    val canUseNativePlayer = source is PlaybackSource.Direct && mediaSurface != null
    val providerAdapter = (source as? PlaybackSource.ProviderControlled)?.let {
        providerAdapters[it.reference.provider]
    }
    val canUseProviderPlayer = providerAdapter != null
    val canControlProviderPlayer = canUseProviderPlayer && providerSession?.capabilities?.canPlayPause == true

    BoxWithConstraints(modifier.fillMaxSize().background(MediaTheme.colors.background)) {
        val contentPadding = if (maxWidth < 600.dp) MediaSpacing.md else MediaSpacing.xxl
        Column(
            Modifier.fillMaxSize().widthIn(max = 1_200.dp).align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = contentPadding, vertical = MediaSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(MediaSpacing.xl)
        ) {
            Box(
                Modifier.fillMaxWidth().widthIn(max = 1_000.dp).align(Alignment.CenterHorizontally)
                    .aspectRatio(16f / 9f)
                    .background(MediaTheme.colors.playerBackground, MediaShapes.large),
                contentAlignment = Alignment.Center
            ) {
                when {
                    canUseNativePlayer -> mediaSurface?.invoke(Modifier.fillMaxSize())
                    canUseProviderPlayer -> providerAdapter?.Surface(
                        providerSession,
                        media,
                        state.positionMs,
                        Modifier.fillMaxSize()
                    )
                    source is PlaybackSource.ProviderControlled -> ProviderPlaybackUnavailable(
                        title = media.catalogItem.title,
                        thumbnailUrl = media.catalogItem.thumbnailUrl
                    )
                    else -> ErrorState(
                        title = "Playback unavailable",
                        message = "This video cannot currently be played inside the application."
                    )
                }
                if ((canUseNativePlayer || canUseProviderPlayer) && state.playbackState.isLoadingIndicatorVisible) {
                    CircularProgressIndicator(color = MediaTheme.colors.primary)
                }
            }

            Column(
                Modifier.fillMaxWidth().widthIn(max = 1_000.dp).align(Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(MediaSpacing.lg)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(MediaSpacing.md), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MediaSpacing.xs)) {
                        Text(
                            media?.catalogItem?.title ?: "Player",
                            style = MediaTheme.typography.screenTitle,
                            color = MediaTheme.colors.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        media?.catalogItem?.authorTitle?.let { MetadataText(it) }
                    }
                    media?.catalogItem?.reference?.provider?.value?.let { ProviderBadge(it) }
                }

                if (canUseNativePlayer || canControlProviderPlayer) {
                    PlaybackControls(
                        state = state,
                        onPlay = onPlay,
                        onPause = onPause,
                        onSeek = onSeek,
                        canSeek = canUseNativePlayer || providerSession?.capabilities?.canSeek == true
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(MediaSpacing.sm)) {
                    androidx.compose.material3.OutlinedButton(onClick = { onSetFavorite(!state.isFavorite) }) {
                        Icon(if (state.isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, null)
                        Text(if (state.isFavorite) "Favorited" else "Favorite", Modifier.padding(start = MediaSpacing.xs))
                    }
                    androidx.compose.material3.OutlinedButton(onClick = { onSetWatchLater(!state.isWatchLater) }) {
                        Icon(Icons.Outlined.WatchLater, null)
                        Text(if (state.isWatchLater) "In Watch Later" else "Watch Later", Modifier.padding(start = MediaSpacing.xs))
                    }
                }

                state.error?.takeUnless {
                    it == PlayerError.UnsupportedMedia &&
                        source is PlaybackSource.ProviderControlled &&
                        providerAdapter == null
                }?.let { error ->
                    ErrorState(
                        title = "Playback interrupted",
                        message = errorMessage(error),
                        onRetry = onRetry
                    )
                }

                media?.let {
                    PlayerDetails(
                        media = it,
                        positionMs = state.positionMs,
                        durationMs = state.durationMs ?: it.catalogItem.durationMs
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerDetails(
    media: PlayableMedia,
    positionMs: Long,
    durationMs: Long?
) {
    val item = media.catalogItem
    val providerName = providerDisplayName(item.reference.provider.value)
    val creatorName = item.authorTitle?.takeIf(String::isNotBlank) ?: providerName
    val creatorInitial = creatorName.firstOrNull()?.uppercaseChar()?.toString() ?: "•"
    val playbackLabel = when (media.source) {
        is PlaybackSource.Direct -> "Native in-app player"
        is PlaybackSource.ProviderControlled -> "Embedded provider player"
    }

    Column(verticalArrangement = Arrangement.spacedBy(MediaSpacing.lg)) {
        Surface(
            color = MediaTheme.colors.surface,
            shape = MediaShapes.large,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(MediaSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MediaSpacing.md)
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = MediaTheme.colors.surfaceSelected
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            creatorInitial,
                            style = MediaTheme.typography.sectionTitle,
                            color = MediaTheme.colors.primary
                        )
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MediaSpacing.xxs)) {
                    Text(
                        creatorName,
                        style = MediaTheme.typography.cardTitle,
                        color = MediaTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (item.authorTitle.isNullOrBlank()) "Media provider" else "Channel",
                        style = MediaTheme.typography.metadata,
                        color = MediaTheme.colors.textTertiary
                    )
                }
                ProviderBadge(providerName)
            }
        }

        Surface(
            color = MediaTheme.colors.surfaceElevated,
            shape = MediaShapes.large,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier.padding(MediaSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(MediaSpacing.md)
            ) {
                Text(
                    "About this video",
                    style = MediaTheme.typography.sectionTitle,
                    color = MediaTheme.colors.textPrimary
                )
                Text(
                    "Playback stays inside the application using the source's supported player experience.",
                    style = MediaTheme.typography.secondaryBody,
                    color = MediaTheme.colors.textSecondary
                )
                HorizontalDivider(color = MediaTheme.colors.divider)
                PlayerInformationRow("Source", providerName)
                PlayerInformationRow("Playback", playbackLabel)
                durationMs?.takeIf { it > 0 }?.let {
                    PlayerInformationRow("Duration", formatTime(it))
                }
                if (positionMs > 0) {
                    PlayerInformationRow("Current position", formatTime(positionMs))
                }
            }
        }
    }
}

@Composable
private fun PlayerInformationRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MediaSpacing.md),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            modifier = Modifier.weight(0.42f),
            style = MediaTheme.typography.metadata,
            color = MediaTheme.colors.textTertiary
        )
        Text(
            value,
            modifier = Modifier.weight(0.58f),
            style = MediaTheme.typography.secondaryBody,
            color = MediaTheme.colors.textPrimary
        )
    }
}

private fun providerDisplayName(value: String): String = when (value.lowercase()) {
    "youtube" -> "YouTube"
    "direct" -> "Direct media"
    else -> value.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

@Composable
private fun ProviderPlaybackUnavailable(
    title: String,
    thumbnailUrl: String?
) {
    Box(Modifier.fillMaxSize()) {
        MediaThumbnail(thumbnailUrl, "Artwork for $title", Modifier.fillMaxSize(), aspectRatio = 16f / 9f)
        Box(Modifier.fillMaxSize().background(MediaTheme.colors.overlay))
        Column(
            Modifier.align(Alignment.Center).padding(MediaSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MediaSpacing.sm)
        ) {
            Icon(Icons.Outlined.LiveTv, null, tint = MediaTheme.colors.playerControls, modifier = Modifier.size(42.dp))
            Text("Playback unavailable", style = MediaTheme.typography.sectionTitle, color = MediaTheme.colors.playerControls)
            Text(
                "This video cannot currently be played inside the application.",
                style = MediaTheme.typography.secondaryBody,
                color = MediaTheme.colors.playerControls.copy(alpha = 0.78f)
            )
        }
    }
}

@Composable
private fun PlaybackControls(
    state: PlayerUiState,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSeek: (Long) -> Unit,
    canSeek: Boolean
) {
    val duration = state.durationMs?.takeIf { it > 0 }
    val isReplay = state.playbackState == PlaybackState.Completed
    Column(verticalArrangement = Arrangement.spacedBy(MediaSpacing.xs)) {
        Slider(
            value = if (duration == null) 0f else state.positionMs.coerceIn(0, duration).toFloat(),
            onValueChange = { onSeek(it.toLong()) },
            enabled = duration != null && canSeek,
            valueRange = 0f..(duration?.toFloat() ?: 1f),
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = MediaTheme.colors.primary,
                activeTrackColor = MediaTheme.colors.primary,
                inactiveTrackColor = MediaTheme.colors.surfaceInteractive
            )
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FilledIconButton(
                onClick = if (state.isPlaying) onPause else onPlay,
                modifier = Modifier.size(56.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MediaTheme.colors.primary,
                    contentColor = MediaTheme.colors.onPrimary
                )
            ) {
                Icon(
                    if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    when {
                        state.isPlaying -> "Pause"
                        isReplay -> "Replay"
                        else -> "Play"
                    },
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                "${formatTime(state.positionMs)}  /  ${duration?.let(::formatTime) ?: "–:––"}",
                modifier = Modifier.padding(start = MediaSpacing.md),
                style = MediaTheme.typography.metadata,
                color = MediaTheme.colors.textSecondary
            )
        }
    }
}

private val PlaybackState.isLoadingIndicatorVisible: Boolean
    get() = this == PlaybackState.Loading || this == PlaybackState.Buffering

private fun errorMessage(error: PlayerError): String = when (error) {
    PlayerError.UnsupportedMedia -> "This source isn’t supported by the current player."
    PlayerError.NetworkFailure -> "The connection was interrupted while loading this media."
    PlayerError.SourceUnavailable -> "The media source is no longer available."
    PlayerError.InitializationFailed -> "The player couldn’t be prepared on this device."
    PlayerError.PlaybackFailed, PlayerError.Unknown -> "We couldn’t continue playback."
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0) / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    else "$minutes:${seconds.toString().padStart(2, '0')}"
}
