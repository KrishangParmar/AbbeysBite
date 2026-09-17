package com.abbeysbite.app.ads

import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.abbeysbite.app.core.designsystem.AppCard
import com.abbeysbite.app.core.designsystem.Dimens
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.revenuecat.purchases.admob.forNativeAdWithTracking
import org.koin.compose.koinInject

/**
 * Native "Sponsored" card inside the Community feed — the ONLY free-tier ad
 * placement. Loads through RevenueCat's AdMob tracking wrapper so loaded /
 * displayed / opened / revenue events reach the same RevenueCat customer.
 * Renders nothing at all for premium users or when serving is disabled.
 */
@Composable
actual fun CommunityNativeAdCard(modifier: Modifier) {
    val adsManager = koinInject<AdsManager>()
    val enabled by adsManager.adsEnabled.collectAsState()
    if (!enabled) return
    val androidAds = adsManager as? AndroidAdsManager ?: return

    val context = LocalContext.current
    val nativeAdState = remember { mutableStateOf<NativeAd?>(null) }
    val failed = remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val unitId = androidAds.nativeCommunityAdUnitId
        val loader = AdLoader.Builder(context, unitId)
            .forNativeAdWithTracking(
                unitId,
                "community_feed",
                object : AdListener() {
                    override fun onAdLoaded() = androidAds.onAdEvent(AdEvent.LOADED)
                    override fun onAdImpression() = androidAds.onAdEvent(AdEvent.IMPRESSION)
                    override fun onAdOpened() = androidAds.onAdEvent(AdEvent.OPENED)
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        failed.value = true
                        androidAds.onAdEvent(AdEvent.FAILED, error.code.toString())
                    }
                },
                { adValue ->
                    androidAds.onAdEvent(
                        AdEvent.REVENUE,
                        "${adValue.valueMicros}:${adValue.currencyCode}",
                    )
                },
            ) { ad -> nativeAdState.value = ad }
            .build()
        loader.loadAd(AdRequest.Builder().build())
        onDispose {
            nativeAdState.value?.destroy()
            nativeAdState.value = null
        }
    }

    val nativeAd = nativeAdState.value
    if (failed.value || nativeAd == null) return // no skeleton for ads — silent

    AppCard(modifier = modifier.fillMaxWidth()) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().padding(Dimens.md),
            factory = { ctx ->
                val adView = NativeAdView(ctx)
                val density = ctx.resources.displayMetrics.density
                fun dp(v: Int) = (v * density).toInt()

                val column = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                }
                val badge = TextView(ctx).apply {
                    text = "Sponsored"
                    textSize = 11f
                    alpha = 0.6f
                }
                val headline = TextView(ctx).apply {
                    textSize = 16f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setPadding(0, dp(4), 0, 0)
                }
                val body = TextView(ctx).apply {
                    textSize = 13f
                    alpha = 0.8f
                    maxLines = 2
                    setPadding(0, dp(2), 0, dp(6))
                }
                val media = MediaView(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(160),
                    )
                }
                val cta = Button(ctx).apply {
                    textSize = 13f
                    isAllCaps = false
                    gravity = Gravity.CENTER
                }
                column.addView(badge)
                column.addView(headline)
                column.addView(body)
                column.addView(media)
                column.addView(cta)
                adView.addView(column)

                adView.headlineView = headline
                adView.bodyView = body
                adView.mediaView = media
                adView.callToActionView = cta
                adView
            },
            update = { adView ->
                (adView.headlineView as TextView).text = nativeAd.headline.orEmpty()
                (adView.bodyView as TextView).text = nativeAd.body.orEmpty()
                (adView.callToActionView as Button).text = nativeAd.callToAction ?: "Learn more"
                adView.setNativeAd(nativeAd)
            },
        )
    }
}
