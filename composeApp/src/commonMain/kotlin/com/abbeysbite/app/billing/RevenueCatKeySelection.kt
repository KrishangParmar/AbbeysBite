package com.abbeysbite.app.billing

/**
 * Store-aware RevenueCat public SDK key selection, extracted as a pure
 * function so the release-safety rules are unit-testable:
 *
 *  - galaxy builds ALWAYS use the galx_ key (GalaxyBillingMode.TEST covers
 *    debug testing on-device; the Test Store key would bypass Galaxy billing).
 *  - play/appstore DEBUG builds prefer the RevenueCat Test Store key so
 *    purchase flows are exercisable without store credentials.
 *  - RELEASE builds can never receive the Test Store key.
 */
object RevenueCatKeySelection {

    fun selectApiKey(
        storeVariant: String,
        isDebug: Boolean,
        googleKey: String,
        galaxyKey: String,
        appleKey: String,
        testStoreKey: String,
    ): String {
        val productionKey = when (storeVariant) {
            "galaxy" -> galaxyKey
            "appstore" -> appleKey
            else -> googleKey
        }
        return when {
            storeVariant == "galaxy" -> productionKey
            isDebug && testStoreKey.isNotBlank() -> testStoreKey
            else -> productionKey
        }
    }
}
