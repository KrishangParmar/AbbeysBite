package com.abbeysbite.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Embedded YouTube player using the official IFrame embed inside a platform
 * web view — compliant with YouTube ToS (no stream extraction). Callers must
 * pass a validated video id (see Validators.youtubeVideoId) and should offer
 * an external-open fallback alongside.
 */
@Composable
expect fun PlatformYouTubePlayer(videoId: String, modifier: Modifier)
