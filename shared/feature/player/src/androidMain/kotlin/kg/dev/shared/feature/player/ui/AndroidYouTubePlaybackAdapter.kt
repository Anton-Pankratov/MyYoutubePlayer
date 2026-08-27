package kg.dev.shared.feature.player.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kg.dev.shared.core.common.media.MediaProviders
import kg.dev.shared.feature.player.PlayableMedia
import kg.dev.shared.feature.player.PlayerError
import kg.dev.shared.feature.player.PlayerState
import kg.dev.shared.feature.player.PlaybackSource
import kg.dev.shared.feature.player.PlaybackState
import kg.dev.shared.feature.player.ProviderMediaSurface
import kg.dev.shared.feature.player.ProviderPlaybackAdapter
import kg.dev.shared.feature.player.ProviderPlaybackCapabilities
import kg.dev.shared.feature.player.ProviderPlaybackSession
import kg.dev.shared.feature.player.youtubePlayerHtml
import kg.dev.shared.feature.player.YOUTUBE_EMBED_APP_ORIGIN
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Official YouTube IFrame Player API embedded inside the application's Android WebView. */
object AndroidYouTubePlaybackAdapter : ProviderPlaybackAdapter {
    override val providerId = MediaProviders.YouTube

    override fun createSession(media: PlayableMedia): ProviderPlaybackSession? {
        val source = media.source as? PlaybackSource.ProviderControlled ?: return null
        return AndroidYouTubePlaybackSession(source.reference.externalId)
    }

    @Composable
    override fun Surface(
        session: ProviderPlaybackSession?,
        media: PlayableMedia,
        startPositionMs: Long,
        modifier: Modifier
    ) {
        val youtubeSession = session as? AndroidYouTubePlaybackSession ?: return
        AndroidYouTubePlayerSurface(youtubeSession, modifier)
    }
}

private class AndroidYouTubePlaybackSession(
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

    private var webView: WebView? = null
    private var media: PlayableMedia? = null
    private var released = false
    private var ready = false
    private var pendingPlay = false
    private var pendingSeekMs: Long? = null

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
        val safePositionMs = positionMs.coerceAtLeast(0)
        pendingSeekMs = safePositionMs
        if (ready) applyPendingSeek()
    }

    override fun retry() {
        if (released || media == null) return
        val recoverablePosition = mutableState.value.positionMs.takeIf { it > 0 }
        if (recoverablePosition != null) pendingSeekMs = recoverablePosition
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
        webView?.post {
            webView?.removeJavascriptInterface(BRIDGE_NAME)
            webView?.stopLoading()
        }
        webView = null
    }

    fun attachWebView(view: WebView) {
        if (released) return
        webView = view
        view.addJavascriptInterface(Bridge(this), BRIDGE_NAME)
        if (media != null) loadPlayerHtml()
    }

    fun detachWebView(view: WebView) {
        if (webView !== view) return
        view.removeJavascriptInterface(BRIDGE_NAME)
        webView = null
        ready = false
    }

    fun onReady() {
        if (released) return
        ready = true
        publish(PlaybackState.Ready)
        applyPendingSeek()
        if (pendingPlay) evaluate("player.playVideo();")
    }

    fun onStateChanged(code: String, positionSeconds: Double, durationSeconds: Double) {
        if (released) return
        val playbackState = when (code) {
            "-1" -> PlaybackState.Loading
            "0" -> PlaybackState.Completed
            "1" -> PlaybackState.Playing
            "2" -> PlaybackState.Paused
            "3" -> PlaybackState.Buffering
            "5" -> PlaybackState.Ready
            else -> PlaybackState.Ready
        }
        publish(
            playbackState = playbackState,
            positionMs = secondsToMilliseconds(positionSeconds),
            durationMs = secondsToMilliseconds(durationSeconds).takeIf { it > 0 }
        )
    }

    fun onError(code: String) {
        if (released) return
        val error = when (code) {
            "2" -> PlayerError.UnsupportedMedia
            "100", "101", "150" -> PlayerError.SourceUnavailable
            "5" -> PlayerError.PlaybackFailed
            else -> PlayerError.PlaybackFailed
        }
        publish(PlaybackState.Error(error))
    }

    fun onProgress(positionSeconds: Double, durationSeconds: Double) {
        if (released) return
        publish(
            playbackState = mutableState.value.playbackState,
            positionMs = secondsToMilliseconds(positionSeconds),
            durationMs = secondsToMilliseconds(durationSeconds).takeIf { it > 0 }
        )
    }

    fun onWebError() {
        if (!released) publish(PlaybackState.Error(PlayerError.NetworkFailure))
    }

    private fun loadPlayerHtml() {
        val playerHtml = youtubePlayerHtml(videoId) ?: return onError("2")
        val target = webView ?: return
        target.post {
            if (!released) {
                target.loadDataWithBaseURL(
                    YOUTUBE_EMBED_APP_ORIGIN,
                    playerHtml,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        }
    }

    private fun applyPendingSeek() {
        val positionMs = pendingSeekMs ?: return
        pendingSeekMs = null
        evaluate("player.seekTo(${positionMs / 1_000.0}, true);")
    }

    private fun evaluate(script: String) {
        val target = webView ?: return
        target.post {
            if (!released && ready) target.evaluateJavascript(script, null)
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

    private class Bridge(private val session: AndroidYouTubePlaybackSession) {
        @JavascriptInterface fun onReady() = session.onReady()
        @JavascriptInterface fun onStateChanged(code: String, position: Double, duration: Double) =
            session.onStateChanged(code, position, duration)
        @JavascriptInterface fun onError(code: String) = session.onError(code)
        @JavascriptInterface fun onProgress(position: Double, duration: Double) =
            session.onProgress(position, duration)
    }

    private companion object {
        const val BRIDGE_NAME = "AndroidPlayerBridge"

        fun secondsToMilliseconds(value: Double): Long =
            if (value.isFinite() && value > 0) (value * 1_000).toLong() else 0
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun AndroidYouTubePlayerSurface(session: AndroidYouTubePlaybackSession, modifier: Modifier) {
    val holder = remember(session) { WebViewHolder() }
    AndroidView(
        factory = { context ->
            YouTubePlayerHostView(context).also { host ->
                val view = host.webView
                view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    view.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
                }
                view.setBackgroundColor(Color.BLACK)
                view.settings.javaScriptEnabled = true
                view.settings.domStorageEnabled = true
                view.settings.mediaPlaybackRequiresUserGesture = true
                view.settings.allowFileAccess = false
                view.settings.allowContentAccess = false
                view.settings.setSupportMultipleWindows(false)
                view.webViewClient = YouTubeWebViewClient(errorCallback = session::onWebError)
                view.webChromeClient = YouTubeWebChromeClient(host::showCustomView, host::hideCustomView)
                holder.value = view
                session.attachWebView(view)
            }
        },
        update = {},
        modifier = modifier
    )
    DisposableEffect(holder) {
        onDispose {
            holder.value?.let { view ->
                session.detachWebView(view)
                view.stopLoading()
                view.removeAllViews()
                view.destroy()
            }
            holder.value = null
        }
    }
}
