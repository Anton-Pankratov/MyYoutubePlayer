package kg.dev.shared.appshell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kg.dev.shared.core.common.media.MediaCatalogItem
import kg.dev.shared.core.common.media.MediaProviders
import kg.dev.shared.core.common.media.MediaReference
import kg.dev.shared.core.ui.design.MediaAppTheme
import kg.dev.shared.core.ui.design.MediaTheme
import kg.dev.shared.feature.history.domain.ResumePolicy
import kg.dev.shared.feature.history.presentation.HistoryItemUiModel
import kg.dev.shared.feature.history.presentation.HistoryUiState
import kg.dev.shared.feature.history.ui.HistoryContent
import kg.dev.shared.feature.home.presentation.HomeMediaItemUiModel
import kg.dev.shared.feature.home.presentation.HomeUiState
import kg.dev.shared.feature.home.ui.HomeContent
import kg.dev.shared.feature.player.PlayableMedia
import kg.dev.shared.feature.player.PlaybackState
import kg.dev.shared.feature.player.PlaybackSource
import kg.dev.shared.feature.player.presentation.PlayerUiState
import kg.dev.shared.feature.player.ui.PlayerContent
import kg.dev.shared.feature.search.domain.model.Channel
import kg.dev.shared.feature.search.presentation.SearchUiState
import kg.dev.shared.feature.search.ui.SearchContent
import org.jetbrains.compose.ui.tooling.preview.Preview

private val previewChannels = listOf(
    Channel("channel-1", "Field Notes Studio", "Thoughtful films about design, culture, and the places between.", null),
    Channel("channel-2", "Northline Sessions", "Independent conversations with people who make things.", null),
    Channel("channel-3", "The Long Cut", "Visual essays for curious minds.", null)
)

private val previewMedia = MediaCatalogItem(
    reference = MediaReference(MediaProviders.YouTube, "video-1"),
    title = "Why quiet interfaces feel more confident",
    thumbnailUrl = null,
    authorTitle = "Field Notes Studio",
    durationMs = 742_000
)

@Preview
@Composable
private fun SearchDarkPreview() = PreviewTheme(dark = true) {
    SearchContent(SearchUiState(query = "Design", items = previewChannels), {}, {}, {}, {}, {}, {}, Modifier.fillMaxSize())
}

@Preview
@Composable
private fun HomeDarkPreview() = PreviewTheme(dark = true) {
    val item = HomeMediaItemUiModel(
        previewMedia.reference, previewMedia.title, null, 238_000, 742_000, 0,
        ResumePolicy.evaluate(238_000, 742_000), true
    )
    HomeContent(HomeUiState(continueWatching = listOf(item), recentlyWatched = listOf(item)), {}, modifier = Modifier.fillMaxSize())
}

@Preview
@Composable
private fun HistoryDarkPreview() = PreviewTheme(dark = true) {
    HistoryContent(
        HistoryUiState(items = listOf(
            HistoryItemUiModel(previewMedia.reference, previewMedia.title, null, 238_000, 742_000, 0, ResumePolicy.evaluate(238_000, 742_000)),
            HistoryItemUiModel(MediaReference(MediaProviders.Direct, "film-2"), "Crafting a visual rhythm", null, 92_000, 480_000, 0, ResumePolicy.evaluate(92_000, 480_000))
        )),
        {},
        modifier = Modifier.fillMaxSize()
    )
}

@Preview
@Composable
private fun HistoryEmptyLightPreview() = PreviewTheme(dark = false) {
    HistoryContent(HistoryUiState(), {}, modifier = Modifier.fillMaxSize())
}

@Preview
@Composable
private fun PlayerDarkPreview() = PreviewTheme(dark = true) {
    PlayerContent(
        state = PlayerUiState(
            media = PlayableMedia(previewMedia.copy(reference = MediaReference(MediaProviders.Direct, "direct-1")), PlaybackSource.Direct("preview")),
            positionMs = 238_000,
            durationMs = 742_000,
            playbackState = PlaybackState.Playing
        ),
        onPlay = {}, onPause = {}, onSeek = {}, onRetry = {},
        mediaSurface = { Box(it.background(MediaTheme.colors.playerBackground)) },
        modifier = Modifier.fillMaxSize()
    )
}

@Preview
@Composable
private fun PlayerUnavailableLightPreview() = PreviewTheme(dark = false) {
    PlayerContent(
        state = PlayerUiState(media = PlayableMedia(previewMedia, PlaybackSource.ProviderControlled(previewMedia.reference))),
        onPlay = {}, onPause = {}, onSeek = {}, onRetry = {},
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun PreviewTheme(dark: Boolean, content: @Composable () -> Unit) {
    MediaAppTheme(darkTheme = dark) { content() }
}
