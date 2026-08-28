package kg.dev.shared.feature.player.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kg.dev.shared.core.common.media.MediaProviders
import kg.dev.shared.feature.player.PlayableMedia
import kg.dev.shared.feature.player.PlayerError
import kg.dev.shared.feature.player.PlayerState
import kg.dev.shared.feature.player.PlaybackSource
import kg.dev.shared.feature.player.PlaybackState
import kg.dev.shared.feature.player.ProviderPlaybackAdapter
import kg.dev.shared.feature.player.ProviderPlaybackCapabilities
import kg.dev.shared.feature.player.ProviderPlaybackSession
import kg.dev.shared.feature.player.YOUTUBE_EMBED_APP_ORIGIN
import kg.dev.shared.feature.player.youtubePlayerHtml
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSURL
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/** Official YouTube IFrame Player API embedded inside the application's iOS WKWebView. */
object IosYouTubePlaybackAdapter : ProviderPlaybackAdapter {
    override val providerId = MediaProviders.YouTube

    override fun createSession(media: PlayableMedia): ProviderPlaybackSession? {
        val source = media.source as? PlaybackSource.ProviderControlled ?: return null
        return IosYouTubePlaybackSession(source.reference.externalId)
    }

    @Composable
    override fun Surface(
        session: ProviderPlaybackSession?,
        media: PlayableMedia,
        startPositionMs: Long,
        modifier: Modifier
    ) {
        val youtubeSession = session as? IosYouTubePlaybackSession ?: return
        IosYouTubePlayerSurface(youtubeSession, modifier)
    }
}

@OptIn(ExperimentalForeignApi::class)
internal class IosYouTubePlaybackSession(
    private val videoId: String
) : ProviderPlaybackSession {
    private val mutableState = MutableStateFlow(PlayerState(playbackState = PlaybackState.Idle))
    override val state: StateFlow<PlayerState> = mutableState.asStateFlow()
    override val capabilities = ProviderPlaybackCapabilities(
        canPlayPause = true,
        canSeek = true,
        reportsPosition = true,
        reportsDuration = true
    )

    private var webView: WKWebView? = null
    private var media: PlayableMedia? = null
    private var released = false
    private var ready = false
    private var pendingPlay = false
    private var pendingSeekMs: Long? = null
    private var bridge: IosYouTubeBridge? = null

    override suspend fun load(media: PlayableMedia) {
        if (youtubePlayerHtml(videoId) == null) {
            publish(PlaybackState.Error(PlayerError.UnsupportedMedia))
            return
        }
        this.media = media
        released = false
        ready = false
        pendingPlay = true
        publish(PlaybackState.Loading)
        loadPlayerHtml()
    }

    override fun play() {
        if (released) return
        pendingPlay = true
        if (ready) evaluate("player.playVideo();")
    }

    override fun pause() {
        pendingPlay = false
        if (ready) evaluate("player.pauseVideo();")
    }

    override fun seekTo(positionMs: Long) {
        pendingSeekMs = positionMs.coerceAtLeast(0)
        if (ready) applyPendingSeek()
    }

    override fun retry() {
        if (released || media == null) return
        mutableState.value.positionMs.takeIf { it > 0 }?.let { pendingSeekMs = it }
        ready = false
        pendingPlay = true
        publish(PlaybackState.Loading)
        loadPlayerHtml()
    }

    override fun release() {
        if (released) return
        released = true
        pendingPlay = false
        ready = false
        runOnMain {
            webView?.let(::detachWebViewOnMain)
            webView = null
        }
    }

    fun attachWebView(view: WKWebView) {
        if (released) return
        webView = view
        val handler = IosYouTubeBridge(::onBridgeMessage)
        bridge = handler
        view.configuration.userContentController.addScriptMessageHandler(handler, BRIDGE_NAME)
        if (media != null) loadPlayerHtml()
    }

    fun detachWebView(view: WKWebView) {
        if (webView !== view) return
        detachWebViewOnMain(view)
        webView = null
        ready = false
    }

    private fun detachWebViewOnMain(view: WKWebView) {
        view.stopLoading()
        view.configuration.userContentController.removeScriptMessageHandlerForName(BRIDGE_NAME)
        bridge?.clear()
        bridge = null
    }

    private fun onBridgeMessage(message: String) {
        if (released) return
        when (val event = parseIosYouTubeBridgeMessage(message)) {
            IosYouTubeBridgeEvent.Ready -> onReady()
            is IosYouTubeBridgeEvent.State -> onStateChanged(event.code, event.positionSeconds, event.durationSeconds)
            is IosYouTubeBridgeEvent.Error -> onError(event.code)
            is IosYouTubeBridgeEvent.Progress -> onProgress(event.positionSeconds, event.durationSeconds)
            null -> Unit
        }
    }

    private fun onReady() {
        if (released) return
        ready = true
        publish(PlaybackState.Ready)
        applyPendingSeek()
        if (pendingPlay) evaluate("player.playVideo();")
    }

    private fun onStateChanged(code: String, positionSeconds: Double, durationSeconds: Double) {
        if (released) return
        publish(
            playbackState = iosYouTubePlaybackState(code),
            positionMs = secondsToMilliseconds(positionSeconds),
            durationMs = secondsToMilliseconds(durationSeconds).takeIf { it > 0 }
        )
    }

    private fun onError(code: String) {
        if (released) return
        val error = when (code) {
            "2" -> PlayerError.UnsupportedMedia
            "100", "101", "150" -> PlayerError.SourceUnavailable
            "5" -> PlayerError.PlaybackFailed
            else -> PlayerError.PlaybackFailed
        }
        publish(PlaybackState.Error(error))
    }

    private fun onProgress(positionSeconds: Double, durationSeconds: Double) {
        if (released) return
        publish(
            playbackState = mutableState.value.playbackState,
            positionMs = secondsToMilliseconds(positionSeconds),
            durationMs = secondsToMilliseconds(durationSeconds).takeIf { it > 0 }
        )
    }

    private fun loadPlayerHtml() {
        val html = youtubePlayerHtml(videoId) ?: return onError("2")
        runOnMain {
            if (!released) webView?.loadHTMLString(html, NSURL(string = YOUTUBE_EMBED_APP_ORIGIN))
        }
    }

    private fun applyPendingSeek() {
        val positionMs = pendingSeekMs ?: return
        pendingSeekMs = null
        evaluate("player.seekTo(${positionMs / 1_000.0}, true);")
    }

    private fun evaluate(script: String) {
        runOnMain {
            if (!released && ready) webView?.evaluateJavaScript(script, completionHandler = null)
        }
    }

    private fun publish(
        playbackState: PlaybackState,
        positionMs: Long = mutableState.value.positionMs,
        durationMs: Long? = mutableState.value.durationMs
    ) {
        mutableState.value = PlayerState(
            media = media,
            playbackState = playbackState,
            positionMs = positionMs.coerceAtLeast(0),
            durationMs = durationMs
        )
    }

    private fun runOnMain(block: () -> Unit) {
        dispatch_async(dispatch_get_main_queue()) { block() }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class IosYouTubeBridge(
    private var onMessage: ((String) -> Unit)?
) : NSObject(), WKScriptMessageHandlerProtocol {
    override fun userContentController(
        userContentController: WKUserContentController,
        didReceiveScriptMessage: WKScriptMessage
    ) {
        (didReceiveScriptMessage.body as? String)?.let { onMessage?.invoke(it) }
    }

    fun clear() {
        onMessage = null
    }
}

internal sealed interface IosYouTubeBridgeEvent {
    data object Ready : IosYouTubeBridgeEvent
    data class State(val code: String, val positionSeconds: Double, val durationSeconds: Double) : IosYouTubeBridgeEvent
    data class Error(val code: String) : IosYouTubeBridgeEvent
    data class Progress(val positionSeconds: Double, val durationSeconds: Double) : IosYouTubeBridgeEvent
}

internal fun parseIosYouTubeBridgeMessage(raw: String): IosYouTubeBridgeEvent? {
    val parts = raw.split('|')
    return when (parts.firstOrNull()) {
        "ready" -> IosYouTubeBridgeEvent.Ready
        "state" -> parts.parseTelemetry { code, position, duration -> IosYouTubeBridgeEvent.State(code, position, duration) }
        "error" -> parts.getOrNull(1)?.let(IosYouTubeBridgeEvent::Error)
        "progress" -> parts.parseTelemetry { _, position, duration -> IosYouTubeBridgeEvent.Progress(position, duration) }
        else -> null
    }
}

private inline fun List<String>.parseTelemetry(
    create: (code: String, positionSeconds: Double, durationSeconds: Double) -> IosYouTubeBridgeEvent
): IosYouTubeBridgeEvent? {
    val code = getOrNull(1) ?: return null
    val position = getOrNull(2)?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: return null
    val duration = getOrNull(3)?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: return null
    return create(code, position, duration)
}

internal fun iosYouTubePlaybackState(code: String): PlaybackState = when (code) {
    "-1" -> PlaybackState.Loading
    "0" -> PlaybackState.Completed
    "1" -> PlaybackState.Playing
    "2" -> PlaybackState.Paused
    "3" -> PlaybackState.Buffering
    "5" -> PlaybackState.Ready
    else -> PlaybackState.Ready
}

internal fun secondsToMilliseconds(value: Double): Long =
    if (value.isFinite() && value > 0) (value * 1_000).toLong() else 0

private const val BRIDGE_NAME = "IosPlayerBridge"

@OptIn(ExperimentalForeignApi::class)
@Composable
private fun IosYouTubePlayerSurface(session: IosYouTubePlaybackSession, modifier: Modifier) {
    val holder = remember(session) { IosWebViewHolder() }
    UIKitView(
        factory = {
            val controller = WKUserContentController().apply {
                addUserScript(
                    WKUserScript(
                        source = IOS_PLAYER_BRIDGE_SCRIPT,
                        injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
                        forMainFrameOnly = true
                    )
                )
            }
            WKWebView(
                CGRectMake(0.0, 0.0, 0.0, 0.0),
                WKWebViewConfiguration().apply { userContentController = controller }
            ).also { view ->
                view.scrollView.scrollEnabled = false
                holder.navigationDelegate = IosYouTubeNavigationDelegate()
                view.navigationDelegate = holder.navigationDelegate
                holder.value = view
                session.attachWebView(view)
            }
        },
        update = {},
        modifier = modifier,
        onRelease = { view ->
            session.detachWebView(view)
            view.navigationDelegate = null
            view.UIDelegate = null
            holder.value = null
        }
    )
    DisposableEffect(holder) {
        onDispose { holder.value?.let(session::detachWebView) }
    }
}

private class IosWebViewHolder(
    var value: WKWebView? = null,
    var navigationDelegate: IosYouTubeNavigationDelegate? = null
)

@OptIn(ExperimentalForeignApi::class)
private class IosYouTubeNavigationDelegate : NSObject(), WKNavigationDelegateProtocol {
    override fun webView(
        webView: WKWebView,
        decidePolicyForNavigationAction: WKNavigationAction,
        decisionHandler: (WKNavigationActionPolicy) -> Unit
    ) {
        val url = decidePolicyForNavigationAction.request.URL?.absoluteString.orEmpty()
        val isAllowedMainFrame = url.startsWith(YOUTUBE_EMBED_APP_ORIGIN) || url == "about:blank"
        decisionHandler(
            if (isAllowedMainFrame || decidePolicyForNavigationAction.targetFrame?.isMainFrame() == false) {
                WKNavigationActionPolicy.WKNavigationActionPolicyAllow
            } else {
                WKNavigationActionPolicy.WKNavigationActionPolicyCancel
            }
        )
    }
}

/** A fixed protocol shim; the page can send only official player lifecycle/progress events. */
private const val IOS_PLAYER_BRIDGE_SCRIPT = """
    window.AndroidPlayerBridge = {
      onReady: function() { window.webkit.messageHandlers.IosPlayerBridge.postMessage('ready'); },
      onStateChanged: function(state, position, duration) {
        window.webkit.messageHandlers.IosPlayerBridge.postMessage('state|' + state + '|' + position + '|' + duration);
      },
      onError: function(code) { window.webkit.messageHandlers.IosPlayerBridge.postMessage('error|' + code); },
      onProgress: function(position, duration) {
        window.webkit.messageHandlers.IosPlayerBridge.postMessage('progress|0|' + position + '|' + duration);
      }
    };
"""
