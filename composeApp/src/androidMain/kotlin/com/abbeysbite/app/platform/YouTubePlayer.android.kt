package com.abbeysbite.app.platform

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun PlatformYouTubePlayer(videoId: String, modifier: Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.mediaPlaybackRequiresUserGesture = true
                webViewClient = WebViewClient()
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                loadDataWithBaseURL(
                    "https://www.youtube.com",
                    youtubeEmbedHtml(videoId),
                    "text/html",
                    "utf-8",
                    null,
                )
            }
        },
        update = { webView ->
            // Reload only when the video changes.
            if (webView.tag != videoId) {
                webView.tag = videoId
                webView.loadDataWithBaseURL(
                    "https://www.youtube.com",
                    youtubeEmbedHtml(videoId),
                    "text/html",
                    "utf-8",
                    null,
                )
            }
        },
    )
}

internal fun youtubeEmbedHtml(videoId: String): String = """
    <!DOCTYPE html>
    <html>
    <head>
      <meta name="viewport" content="width=device-width, initial-scale=1">
      <style>
        html, body { margin:0; padding:0; background:transparent; height:100%; }
        .wrap { position:relative; width:100%; height:100%; }
        iframe { position:absolute; inset:0; width:100%; height:100%; border:0; border-radius:16px; }
      </style>
    </head>
    <body>
      <div class="wrap">
        <iframe
          src="https://www.youtube.com/embed/$videoId?playsinline=1&rel=0"
          allow="accelerometer; encrypted-media; gyroscope; picture-in-picture"
          allowfullscreen>
        </iframe>
      </div>
    </body>
    </html>
""".trimIndent()
