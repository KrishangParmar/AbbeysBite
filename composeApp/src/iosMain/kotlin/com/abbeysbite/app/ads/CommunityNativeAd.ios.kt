package com.abbeysbite.app.ads

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.abbeysbite.app.billing.BillingManager
import com.abbeysbite.app.platform.PlatformInfo
import kotlinx.coroutines.CoroutineScope

/** iOS ads ship with the Apple production phase; policy layer stays shared. */
class IosAdsManager(
    billingManager: BillingManager,
    platformInfo: PlatformInfo,
    scope: CoroutineScope,
) : BaseAdsManager(billingManager, platformInfo, scope) {
    override val platformSupported: Boolean = false
    override fun initialize() = Unit
    override fun onAdEvent(event: AdEvent, detail: String?) = Unit
}

@Composable
actual fun CommunityNativeAdCard(modifier: Modifier) {
    // No iOS ad serving until the Apple phase; premium/free policy unchanged.
}
