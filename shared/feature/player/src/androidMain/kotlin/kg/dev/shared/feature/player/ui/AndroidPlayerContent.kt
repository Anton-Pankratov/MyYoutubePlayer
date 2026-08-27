package kg.dev.shared.feature.player.ui

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
import androidx.compose.foundation.layout.fillMaxSize
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
        providerAdapters = component.providerPlaybackAdapters,
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
internal class YouTubePlayerHostView(context: Context) : FrameLayout(context) {
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

private const val LOG_TAG = "YouTubeInAppPlayer"
