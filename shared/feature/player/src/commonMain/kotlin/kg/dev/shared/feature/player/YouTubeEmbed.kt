package kg.dev.shared.feature.player

private val youtubeVideoIdPattern = Regex("^[A-Za-z0-9_-]{6,64}$")

const val YOUTUBE_EMBED_APP_ORIGIN = "https://kg.dev.videoplayer"

fun youtubeEmbedUrl(videoId: String, startPositionMs: Long = 0): String? {
    if (!youtubeVideoIdPattern.matches(videoId)) return null
    val startSeconds = (startPositionMs.coerceAtLeast(0) / 1_000)
    return buildString {
        append("https://www.youtube.com/embed/")
        append(videoId)
        append("?playsinline=1&rel=0")
        if (startSeconds > 0) append("&start=").append(startSeconds)
    }
}

/** HTML host for the official IFrame Player API. Video IDs are validated before interpolation. */
fun youtubePlayerHtml(videoId: String, startPositionMs: Long = 0): String? {
    if (!youtubeVideoIdPattern.matches(videoId)) return null
    val startSeconds = startPositionMs.coerceAtLeast(0) / 1_000
    return """
        <!doctype html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
          <style>
            html, body, #player { width: 100%; height: 100%; margin: 0; background: #000; overflow: hidden; }
          </style>
        </head>
        <body>
          <div id="player"></div>
          <script src="https://www.youtube.com/iframe_api"></script>
          <script>
            function onYouTubeIframeAPIReady() {
              new YT.Player('player', {
                width: '100%',
                height: '100%',
                videoId: '$videoId',
                playerVars: {
                  playsinline: 1,
                  rel: 0,
                  start: $startSeconds,
                  enablejsapi: 1,
                  origin: '$YOUTUBE_EMBED_APP_ORIGIN'
                },
                events: {
                  onReady: function() { AndroidPlayerBridge.onReady(); },
                  onError: function(event) { AndroidPlayerBridge.onError(String(event.data)); }
                }
              });
            }
          </script>
        </body>
        </html>
    """.trimIndent()
}
