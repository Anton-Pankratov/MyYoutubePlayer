package kg.dev.shared.feature.player.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import kg.dev.shared.feature.player.AndroidVideoPlayerController
import kg.dev.shared.feature.player.presentation.PlayerDisplayMode
import kg.dev.shared.core.ui.navigation.PlaybackQueueState
import kg.dev.shared.feature.player.presentation.DefaultPlayerComponent

internal data class AndroidFullscreenPresentationCallbacks(
    val isFullscreen: Boolean,
    val onEntered: () -> Unit,
    val onExited: () -> Unit,
)

internal val LocalAndroidFullscreenPresentation = staticCompositionLocalOf {
    AndroidFullscreenPresentationCallbacks(
        isFullscreen = false,
        onEntered = {},
        onExited = {},
    )
}

@Composable
fun AndroidPlayerContent(
    component: DefaultPlayerComponent,
    modifier: Modifier = Modifier,
    activeQueue: PlaybackQueueState? = null,
    onSelectQueueItem: (Int) -> Unit = {},
) {
    val state by component.state.collectAsState()
    val isFullscreen = state.displayMode == PlayerDisplayMode.Fullscreen
    var providerCustomFullscreen by remember(component) { mutableStateOf(false) }
    val controller = component.videoPlayerController as? AndroidVideoPlayerController
    val serviceOwnedAudio = component.directPlaybackHost != null
    AndroidFullscreenWindowEffect(isFullscreen)
    CompositionLocalProvider(
        LocalAndroidFullscreenPresentation provides AndroidFullscreenPresentationCallbacks(
            isFullscreen = isFullscreen,
            onEntered = {
                providerCustomFullscreen = true
                component.requestFullscreen()
            },
            onExited = {
                providerCustomFullscreen = false
                component.exitFullscreen()
            },
        ),
    ) {
        PlayerContent(
            component = component,
            modifier = modifier,
            activeQueue = activeQueue,
            onSelectQueueItem = onSelectQueueItem,
            applyFullscreenPresentation = true,
            renderFullscreenSurface = !providerCustomFullscreen,
            providerAdapters = component.providerPlaybackAdapters,
            mediaSurface = if (controller == null || serviceOwnedAudio) null else { surfaceModifier ->
                AndroidView(
                    factory = { context -> PlayerView(context).also {
                        it.player = controller.media3Player
                        it.useController = false
                    } },
                    update = {
                        it.player = controller.media3Player
                        it.useController = false
                    },
                    modifier = surfaceModifier,
                )
                // Component lifecycle, not composition lifecycle, owns release.
                DisposableEffect(controller) { onDispose { } }
            },
        )
    }
}

@Composable
private fun AndroidFullscreenWindowEffect(isFullscreen: Boolean) {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity, isFullscreen) {
        if (!isFullscreen || activity == null) return@DisposableEffect onDispose {}
        val decorView = activity.window.decorView
        @Suppress("DEPRECATION")
        val previousSystemUiVisibility = decorView.systemUiVisibility
        val previousStatusBarsVisible = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            decorView.rootWindowInsets?.isVisible(WindowInsets.Type.statusBars()) ?: true
        } else {
            true
        }
        val previousNavigationBarsVisible = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            decorView.rootWindowInsets?.isVisible(WindowInsets.Type.navigationBars()) ?: true
        } else {
            true
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.window.insetsController?.apply {
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsets.Type.systemBars())
            }
        } else {
            @Suppress("DEPRECATION")
            decorView.systemUiVisibility = FULLSCREEN_SYSTEM_UI_FLAGS
        }

        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity.window.insetsController?.apply {
                    if (previousStatusBarsVisible) show(WindowInsets.Type.statusBars())
                    else hide(WindowInsets.Type.statusBars())
                    if (previousNavigationBarsVisible) show(WindowInsets.Type.navigationBars())
                    else hide(WindowInsets.Type.navigationBars())
                }
            } else {
                @Suppress("DEPRECATION")
                run { decorView.systemUiVisibility = previousSystemUiVisibility }
            }
        }
    }
}

/** Non-observable holder: assigning the platform view must not trigger recomposition. */
internal class WebViewHolder(var value: WebView? = null)

internal class YouTubeWebViewClient(
    private val errorCallback: () -> Unit
) : WebViewClient() {
    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        Log.d(LOG_TAG, "Page started: $url")
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        if (request?.isForMainFrame != true) return false
        Log.w(LOG_TAG, "Blocked main-frame navigation outside embedded player")
        return true
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
    ) {
        Log.e(LOG_TAG, "Resource error ${error?.errorCode}: ${error?.description}; url=${request?.url}")
        if (request?.isForMainFrame == true) errorCallback()
    }

    override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: WebResourceResponse?
    ) {
        Log.e(LOG_TAG, "HTTP ${errorResponse?.statusCode}; url=${request?.url}")
        if (request?.isForMainFrame == true) errorCallback()
    }

    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
        Log.e(LOG_TAG, "SSL error ${error?.primaryError}; url=${error?.url}")
        handler?.cancel()
        errorCallback()
    }
}

internal class YouTubeWebChromeClient(
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
internal class YouTubePlayerHostView(
    context: Context,
    private val onFullscreenEntered: () -> Unit,
    private val onFullscreenExited: () -> Unit,
) : FrameLayout(context) {
    val webView = WebView(context)
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var customViewContainer: ViewGroup? = null
    private var fullscreenOverlay: FrameLayout? = null
    private var fullscreenWindowContainer: ViewGroup? = null

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
        } else {
            customViewContainer = this
            addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
        onFullscreenEntered()
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
        callback?.onCustomViewHidden()
        onFullscreenExited()
    }

    /** Shared display-mode exit must also dismiss a provider-owned custom surface. */
    fun reconcileDisplayMode(isFullscreen: Boolean) {
        if (!isFullscreen) hideCustomView()
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

    override fun onDetachedFromWindow() {
        hideCustomView()
        super.onDetachedFromWindow()
    }

}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val LOG_TAG = "YouTubeInAppPlayer"

@Suppress("DEPRECATION")
private const val FULLSCREEN_SYSTEM_UI_FLAGS =
    View.SYSTEM_UI_FLAG_FULLSCREEN or
        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
