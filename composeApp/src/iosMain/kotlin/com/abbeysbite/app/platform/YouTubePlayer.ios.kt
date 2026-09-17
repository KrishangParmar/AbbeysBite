package com.abbeysbite.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformYouTubePlayer(videoId: String, modifier: Modifier) {
    val webView = remember(videoId) {
        val configuration = WKWebViewConfiguration().apply {
            allowsInlineMediaPlayback = true
        }
        WKWebView(frame = CGRectZero.readValue(), configuration = configuration).apply {
            opaque = false
            loadHTMLString(
                iosYoutubeEmbedHtml(videoId),
                baseURL = platform.Foundation.NSURL(string = "https://www.youtube.com"),
            )
        }
    }
    UIKitView(
        factory = { webView },
        modifier = modifier,
    )
}

private fun iosYoutubeEmbedHtml(videoId: String): String = """
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
