package kg.dev.shared.feature.player.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import kg.dev.shared.feature.player.AndroidVideoPlayerController
import kg.dev.shared.feature.player.presentation.DefaultPlayerComponent

@Composable
fun AndroidPlayerContent(component: DefaultPlayerComponent, modifier: Modifier = Modifier) {
    val controller = component.videoPlayerController as? AndroidVideoPlayerController
    PlayerContent(
        component = component,
        modifier = modifier,
        mediaSurface = if (controller == null) null else { surfaceModifier ->
            AndroidView(
                factory = { context -> PlayerView(context).also { it.player = controller.media3Player } },
                update = { it.player = controller.media3Player },
                modifier = surfaceModifier
            )
            // Component lifecycle, not composition lifecycle, owns release.
            DisposableEffect(controller) { onDispose { } }
        }
    )
}
