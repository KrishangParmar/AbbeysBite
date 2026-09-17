package com.abbeysbite.app.billing

import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.PurchasesConfiguration

/**
 * iOS always targets the App Store — the KMP configuration path is used
 * directly. Production activation waits on the deferred Apple setup
 * (REVENUECAT_IOS_PUBLIC_SDK_KEY), with the Test Store usable in debug.
 */
actual fun configureRevenueCatForStore(
    apiKey: String,
    storeVariant: String,
    isDebugBuild: Boolean,
) {
    Purchases.configure(PurchasesConfiguration(apiKey = apiKey))
}
