package kg.dev.shared.feature.player.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kg.dev.shared.feature.player.youtubeEmbedUrl
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration

@Composable
@OptIn(ExperimentalForeignApi::class)
fun IosYouTubePlayer(videoId: String, startPositionMs: Long, modifier: Modifier = Modifier) {
    val embedUrl = youtubeEmbedUrl(videoId, startPositionMs) ?: return
    UIKitView(
        factory = {
            WKWebView(CGRectMake(0.0, 0.0, 0.0, 0.0), WKWebViewConfiguration()).apply {
                scrollView.scrollEnabled = false
                loadRequest(NSURLRequest(NSURL(string = embedUrl)))
            }
        },
        modifier = modifier,
        update = { webView ->
            if (webView.URL?.absoluteString != embedUrl) {
                webView.loadRequest(NSURLRequest(NSURL(string = embedUrl)))
            }
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.navigationDelegate = null
            webView.UIDelegate = null
        }
    )
}
