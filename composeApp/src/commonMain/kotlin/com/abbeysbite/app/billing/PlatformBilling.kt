package com.abbeysbite.app.billing

/**
 * Configures the RevenueCat SDK for the active distribution store. Exactly one
 * store configuration is ever initialized per process:
 *
 * - `play` / `appstore` / Test Store → standard RevenueCat configuration
 * - `galaxy` → native Android Galaxy store configuration (galx_ key), with
 *   Samsung TEST billing mode in debug builds
 *
 * Implemented per platform because Galaxy support lives in RevenueCat's native
 * Android SDK (purchases-store-galaxy), not in the KMP surface.
 */
expect fun configureRevenueCatForStore(
    apiKey: String,
    storeVariant: String,
    isDebugBuild: Boolean,
)
