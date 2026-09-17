package com.abbeysbite.app.ads

import android.content.Context
import com.abbeysbite.app.analytics.Analytics
import com.abbeysbite.app.billing.BillingManager
import com.abbeysbite.app.core.config.AppSecrets
import com.abbeysbite.app.platform.PlatformInfo
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Android AdMob implementation. Initialization is explicit and lazy —
 * DELAY_APP_MEASUREMENT_INIT is set in the manifest, so nothing ad-related
 * runs for premium users or when serving isn't possible. Ad lifecycle events
 * flow into product analytics; impression/revenue tracking to RevenueCat is
 * attached at ad-load time via RCAdMobNativeAd (see CommunityNativeAd).
 */
class AndroidAdsManager(
    private val context: Context,
    billingManager: BillingManager,
    platformInfo: PlatformInfo,
    private val analytics: Analytics,
    private val scope: CoroutineScope,
) : BaseAdsManager(billingManager, platformInfo, scope) {

    override val platformSupported: Boolean = true

    @Volatile
    private var initialized = false

    override fun initialize() {
        if (initialized || !servingPossible) return
        scope.launch(Dispatchers.IO) {
            runCatching { MobileAds.initialize(context) }
                .onSuccess { initialized = true }
        }
    }

    /** Active native ad unit: Google's official TEST id in debug builds only. */
    val nativeCommunityAdUnitId: String
        get() = if (!platformInfo.isDebug && unitIdsConfigured) {
            AppSecrets.admobNativeCommunityAdUnitId
        } else {
            // Debug must never exercise production inventory, even when a
            // developer has production ids in local configuration.
            "ca-app-pub-3940256099942544/2247696110"
        }

    override fun onAdEvent(event: AdEvent, detail: String?) {
        val name = when (event) {
            AdEvent.LOADED -> "ad_loaded"
            AdEvent.IMPRESSION -> "ad_impression"
            AdEvent.OPENED -> "ad_opened"
            AdEvent.FAILED -> "ad_failed_to_load"
            AdEvent.REVENUE -> "ad_revenue"
        }
        analytics.track(name, buildMap {
            put("placement", "community_feed")
            detail?.let { put("detail", it) }
        })
    }
}
