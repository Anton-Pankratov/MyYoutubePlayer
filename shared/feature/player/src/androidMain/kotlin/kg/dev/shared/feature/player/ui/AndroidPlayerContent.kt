package kg.dev.shared.feature.player.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.net.http.SslError
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.ConsoleMessage
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import kg.dev.shared.core.common.media.MediaProviders
import kg.dev.shared.feature.player.AndroidVideoPlayerController
import kg.dev.shared.feature.player.YOUTUBE_EMBED_APP_ORIGIN
import kg.dev.shared.feature.player.presentation.DefaultPlayerComponent
import kg.dev.shared.feature.player.youtubeEmbedUrl
import kg.dev.shared.core.ui.design.MediaTheme
import kg.dev.shared.core.ui.design.MediaSpacing
import kotlinx.coroutines.delay

@Composable
fun AndroidPlayerContent(component: DefaultPlayerComponent, modifier: Modifier = Modifier) {
    val controller = component.videoPlayerController as? AndroidVideoPlayerController
    val providerAdapters = remember {
        ProviderPlaybackAdapterRegistry(
            listOf(
                ProviderPlaybackAdapter(MediaProviders.YouTube) { reference, startPositionMs, surfaceModifier ->
                    AndroidYouTubePlayer(reference.externalId, startPositionMs, surfaceModifier)
                }
            )
        )
    }
    PlayerContent(
        component = component,
        modifier = modifier,
        providerAdapters = providerAdapters,
        mediaSurface = if (controller == null) null else { surfaceModifier ->
            AndroidView(
                factory = { context -> PlayerView(context).also {
                    it.player = controller.media3Player
                    it.useController = false
                } },
                update = {
                    it.player = controller.media3Player
                    it.useController = false
                },
                modifier = surfaceModifier
            )
            // Component lifecycle, not composition lifecycle, owns release.
            DisposableEffect(controller) { onDispose { } }
        }
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun AndroidYouTubePlayer(videoId: String, startPositionMs: Long, modifier: Modifier) {
    val embedUrl = youtubeEmbedUrl(videoId, startPositionMs)
        ?.replace(PLAY_INLINE_QUERY, USE_CUSTOM_VIEW_QUERY)
        ?: return
    val webViewHolder = remember(embedUrl) { WebViewHolder() }
    var playerState by remember(videoId, startPositionMs) { mutableStateOf<YouTubePlayerState>(YouTubePlayerState.Loading) }

    LaunchedEffect(playerState) {
        if (playerState == YouTubePlayerState.Loading) {
            delay(PLAYER_READY_TIMEOUT_MS)
            if (playerState == YouTubePlayerState.Loading) {
                playerState = YouTubePlayerState.Error("timeout")
            }
        }
    }

    Box(modifier.background(MediaTheme.colors.playerBackground), contentAlignment = Alignment.Center) {
        key(embedUrl) {
            AndroidView(
                factory = { context ->
                    YouTubePlayerHostView(context).also { host ->
                        val view = host.webView
                        webViewHolder.value = view
                        // Inline video must stay in a hardware-backed texture when WebView is
                        // hosted by Compose. A software layer renders HTML but drops video frames.
                        view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            view.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
                        }
                        view.setBackgroundColor(Color.BLACK)
                        view.webViewClient = YouTubeWebViewClient(
                            readyCallback = { playerState = YouTubePlayerState.Ready },
                            errorCallback = { code -> playerState = YouTubePlayerState.Error(code) }
                        )
                        view.webChromeClient = YouTubeWebChromeClient(
                            showCustomView = host::showCustomView,
                            hideCustomView = host::hideCustomView
                        )
                        view.settings.javaScriptEnabled = true
                        view.settings.domStorageEnabled = true
                        view.settings.mediaPlaybackRequiresUserGesture = true
                        view.settings.setSupportMultipleWindows(false)
                        view.loadUrl(embedUrl, mapOf(REFERER_HEADER to YOUTUBE_EMBED_APP_ORIGIN))
                    }
                },
                // Do not call WebView.onResume() here: update runs on ordinary Compose
                // recompositions, not only when the host lifecycle resumes.
                update = {},
                modifier = Modifier.fillMaxSize()
            )
        }
        when (val state = playerState) {
            YouTubePlayerState.Loading -> CircularProgressIndicator(color = MediaTheme.colors.primary)
            YouTubePlayerState.Ready -> Unit
            is YouTubePlayerState.Error -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MediaSpacing.xs),
                modifier = Modifier.padding(MediaSpacing.sm)
            ) {
                Text(
                    "Video unavailable",
                    style = MediaTheme.typography.sectionTitle,
                    color = MediaTheme.colors.error
                )
                Text(
                    youtubeErrorMessage(state.code),
                    style = MediaTheme.typography.metadata,
                    color = MediaTheme.colors.playerControls
                )
                TextButton(onClick = {
                    playerState = YouTubePlayerState.Loading
                    webViewHolder.value?.loadUrl(embedUrl, mapOf(REFERER_HEADER to YOUTUBE_EMBED_APP_ORIGIN))
                }) { Text("Retry") }
            }
        }
    }

    DisposableEffect(webViewHolder) {
        onDispose {
            webViewHolder.value?.apply {
                onPause()
                stopLoading()
                loadUrl("about:blank")
                clearHistory()
                removeAllViews()
                destroy()
            }
            webViewHolder.value = null
        }
    }
}

/** Non-observable holder: assigning the platform view must not trigger recomposition. */
private class WebViewHolder(var value: WebView? = null)

private class YouTubeWebViewClient(
    private val readyCallback: () -> Unit,
    private val errorCallback: (String) -> Unit
) : WebViewClient() {
    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        Log.d(LOG_TAG, "Page started: $url")
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        Log.d(LOG_TAG, "Page finished: $url")
        if (url?.startsWith(YOUTUBE_EMBED_URL_PREFIX) == true) readyCallback()
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        if (request?.isForMainFrame != true) return false
        val url = request.url?.toString().orEmpty()
        val allowed = url.startsWith(YOUTUBE_EMBED_URL_PREFIX)
        if (!allowed) Log.w(LOG_TAG, "Blocked main-frame navigation outside embedded player: $url")
        return !allowed
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
    ) {
        Log.e(LOG_TAG, "Resource error ${error?.errorCode}: ${error?.description}; url=${request?.url}")
        if (request?.isForMainFrame == true) errorCallback("network")
    }

    override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: WebResourceResponse?
    ) {
        Log.e(LOG_TAG, "HTTP ${errorResponse?.statusCode}; url=${request?.url}")
        if (request?.isForMainFrame == true) errorCallback("http-${errorResponse?.statusCode}")
    }

    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
        Log.e(LOG_TAG, "SSL error ${error?.primaryError}; url=${error?.url}")
        handler?.cancel()
        errorCallback("ssl")
    }
}

private class YouTubeWebChromeClient(
    private val showCustomView: (View, CustomViewCallback) -> Unit,
    private val hideCustomView: () -> Unit
) : WebChromeClient() {
    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        if (view == null || callback == null) return
        Log.d(LOG_TAG, "Provider requested fullscreen custom view")
        showCustomView(view, callback)
    }

    override fun onHideCustomView() {
        Log.d(LOG_TAG, "Provider dismissed fullscreen custom view")
        hideCustomView()
    }

    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
        Log.d(
            LOG_TAG,
            "JS ${consoleMessage?.messageLevel()}: ${consoleMessage?.message()} " +
                "(${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()})"
        )
        return true
    }
}

/**
 * Hosts Chromium's native video surface in the Player and promotes that same surface to the
 * Activity window when the official embedded player requests fullscreen.
 */
private class YouTubePlayerHostView(context: Context) : FrameLayout(context) {
    val webView = WebView(context)
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var customViewContainer: ViewGroup? = null
    private var fullscreenOverlay: FrameLayout? = null
    private var fullscreenWindowContainer: ViewGroup? = null
    private var previousRequestedOrientation: Int? = null
    private var previousSystemUiVisibility: Int? = null

    init {
        addView(
            webView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
    }

    fun showCustomView(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (customView != null) {
            callback.onCustomViewHidden()
            return
        }
        customView = view
        customViewCallback = callback
        webView.visibility = View.INVISIBLE
        (view.parent as? ViewGroup)?.removeView(view)

        val activity = context.findActivity()
        val windowContainer = activity?.window?.decorView as? ViewGroup
        Log.d(LOG_TAG, "Attaching custom view; activityFound=${activity != null}, windowFound=${windowContainer != null}")
        if (activity != null && windowContainer != null) {
            val overlay = createFullscreenOverlay(activity, view)
            fullscreenOverlay = overlay
            fullscreenWindowContainer = windowContainer
            customViewContainer = overlay
            windowContainer.addView(
                overlay,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            enterFullscreen(activity, windowContainer)
        } else {
            customViewContainer = this
            addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
    }

    fun hideCustomView() {
        if (customView == null) return
        customView?.let { customViewContainer?.removeView(it) }
        customView = null
        customViewContainer = null
        fullscreenOverlay?.let { fullscreenWindowContainer?.removeView(it) }
        fullscreenOverlay = null
        fullscreenWindowContainer = null
        val callback = customViewCallback
        customViewCallback = null
        webView.visibility = View.VISIBLE
        context.findActivity()?.let(::exitFullscreen)
        callback?.onCustomViewHidden()
    }

    private fun createFullscreenOverlay(activity: Activity, providerView: View): FrameLayout =
        FrameLayout(activity).apply {
            setBackgroundColor(Color.BLACK)
            addView(
                providerView,
                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            )
            addView(
                ImageButton(activity).apply {
                    contentDescription = "Exit full screen"
                    setImageResource(kg.dev.shared.feature.player.R.drawable.ic_fullscreen_exit)
                    setColorFilter(Color.WHITE)
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(0x99000000.toInt())
                    }
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                    setOnClickListener { hideCustomView() }
                },
                LayoutParams(dp(48), dp(48), Gravity.TOP or Gravity.END).apply {
                    topMargin = dp(16)
                    marginEnd = dp(16)
                }
            )
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    @Suppress("DEPRECATION")
    private fun enterFullscreen(activity: Activity, decorView: View) {
        previousRequestedOrientation = activity.requestedOrientation
        previousSystemUiVisibility = decorView.systemUiVisibility
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        hideSystemBars(activity, decorView)
        // Rotation performs another window-insets pass. Re-assert immersive mode afterwards so
        // Android 11+ does not restore status/navigation bars over the video.
        decorView.postDelayed({
            if (customView != null) hideSystemBars(activity, decorView)
        }, FULLSCREEN_RELAYOUT_DELAY_MS)
    }

    private fun hideSystemBars(activity: Activity, decorView: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.window.insetsController?.apply {
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsets.Type.systemBars())
            }
        } else {
            @Suppress("DEPRECATION")
            decorView.systemUiVisibility = FULLSCREEN_SYSTEM_UI_FLAGS
        }
    }

    private fun exitFullscreen(activity: Activity) {
        val decorView = activity.window.decorView
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.window.insetsController?.show(WindowInsets.Type.systemBars())
        } else {
            @Suppress("DEPRECATION")
            previousSystemUiVisibility?.let { decorView.systemUiVisibility = it }
        }
        previousSystemUiVisibility = null
        previousRequestedOrientation?.let { activity.requestedOrientation = it }
        previousRequestedOrientation = null
    }

    override fun onDetachedFromWindow() {
        hideCustomView()
        super.onDetachedFromWindow()
    }

    private companion object {
        @Suppress("DEPRECATION")
        const val FULLSCREEN_SYSTEM_UI_FLAGS =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        const val FULLSCREEN_RELAYOUT_DELAY_MS = 300L
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private sealed interface YouTubePlayerState {
    data object Loading : YouTubePlayerState
    data object Ready : YouTubePlayerState
    data class Error(val code: String) : YouTubePlayerState
}

private fun youtubeErrorMessage(code: String): String = when (code) {
    "100" -> "This video was removed or is private."
    "101", "150" -> "The creator doesn’t allow this video to play in embedded players."
    "153" -> "YouTube couldn’t verify this embedded player."
    "5" -> "YouTube couldn’t initialize HTML5 playback for this video."
    "network" -> "The embedded player couldn’t reach YouTube."
    "ssl" -> "A secure connection to YouTube couldn’t be established."
    "timeout" -> "This video cannot currently be played inside the application."
    else -> if (code.startsWith("http-")) {
        "YouTube returned an unexpected response. Please try again."
    } else {
        "YouTube couldn’t load this video. Please try again."
    }
}

private const val LOG_TAG = "YouTubeInAppPlayer"
private const val YOUTUBE_EMBED_URL_PREFIX = "https://www.youtube.com/embed/"
private const val REFERER_HEADER = "Referer"
private const val PLAYER_READY_TIMEOUT_MS = 8_000L
private const val PLAY_INLINE_QUERY = "playsinline=1"
private const val USE_CUSTOM_VIEW_QUERY = "playsinline=0"
