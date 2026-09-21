package kg.dev.shared.feature.player.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kg.dev.shared.feature.player.IosVideoPlayerController
import kg.dev.shared.core.ui.navigation.PlaybackQueueState
import kg.dev.shared.feature.player.presentation.DefaultPlayerComponent
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerLayer
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIView

@Composable
@OptIn(ExperimentalForeignApi::class)
fun IosPlayerContent(
    component: DefaultPlayerComponent,
    modifier: Modifier = Modifier,
    activeQueue: PlaybackQueueState? = null,
    onSelectQueueItem: (Int) -> Unit = {},
) {
    val controller = component.videoPlayerController as? IosVideoPlayerController
    PlayerContent(
        component = component,
        modifier = modifier,
        activeQueue = activeQueue,
        onSelectQueueItem = onSelectQueueItem,
        providerAdapters = component.providerPlaybackAdapters,
        // Eligible Direct audio is rendered and retained by the process-scoped host, not a UIView.
        mediaSurface = if (controller == null || component.directPlaybackHost != null) null else { surfaceModifier ->
            UIKitView(
                factory = { IosPlayerView().also { it.player = controller.avPlayer } },
                update = { it.player = controller.avPlayer },
                modifier = surfaceModifier,
                onRelease = { it.player = null }
            )
            DisposableEffect(controller) { onDispose { } }
        }
    )
}

@OptIn(ExperimentalForeignApi::class)
private class IosPlayerView : UIView(CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    private val playerLayer = AVPlayerLayer()

    var player: AVPlayer?
        get() = playerLayer.player
        set(value) { playerLayer.player = value }

    init {
        layer.addSublayer(playerLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        playerLayer.frame = bounds
    }
}
