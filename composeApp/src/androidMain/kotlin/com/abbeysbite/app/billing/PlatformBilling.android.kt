package com.abbeysbite.app.billing

import android.content.Context
import android.util.Log
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.galaxy.GalaxyBillingMode
import com.revenuecat.purchases.galaxy.GalaxyConfiguration
import org.koin.core.context.GlobalContext

/**
 * Android store configuration.
 *
 * - `play` (and Test Store in debug): configured through the KMP SDK so the
 *   shared billing code uses its wrapper directly.
 * - `galaxy`: RevenueCat's Galaxy support lives ONLY in the native Android
 *   SDK, so the native singleton is configured with [GalaxyConfiguration]
 *   (TEST billing mode in debug builds, PRODUCTION in release) and the KMP
 *   wrapper is then attached to that SAME native singleton — one
 *   initialization, no duplicate SDK state, and every shared
 *   offerings/purchase/entitlement call works unchanged.
 *
 * Exactly one store configuration is ever initialized per process; the play
 * binary cannot activate Galaxy billing and vice versa (flavor-baked STORE).
 */
actual fun configureRevenueCatForStore(
    apiKey: String,
    storeVariant: String,
    isDebugBuild: Boolean,
) {
    when (storeVariant) {
        "galaxy" -> {
            val context: Context = GlobalContext.get().get()
            Purchases.logLevel = if (isDebugBuild) LogLevel.DEBUG else LogLevel.INFO
            val mode = if (isDebugBuild) GalaxyBillingMode.TEST else GalaxyBillingMode.PRODUCTION
            Log.i("AbbeysBilling", "Configuring RevenueCat for Samsung Galaxy Store (mode=$mode)")
            Purchases.configure(
                GalaxyConfiguration.Builder(context, apiKey)
                    .galaxyBillingMode(mode)
                    .build()
            )
            attachKmpWrapperToNativeSingleton()
        }
        else -> {
            Log.i("AbbeysBilling", "Configuring RevenueCat via KMP for store=$storeVariant")
            com.revenuecat.purchases.kmp.Purchases.logLevel =
                if (isDebugBuild) com.revenuecat.purchases.kmp.LogLevel.DEBUG
                else com.revenuecat.purchases.kmp.LogLevel.INFO
            com.revenuecat.purchases.kmp.Purchases.configure(
                com.revenuecat.purchases.kmp.PurchasesConfiguration(apiKey = apiKey)
            )
        }
    }
}

/**
 * Attaches the KMP `Purchases` wrapper to the natively-configured singleton.
 * The KMP SDK (3.7.0) has no public Galaxy configuration path, but its
 * wrapper is a thin shell over `com.revenuecat.purchases.Purchases` — we
 * construct it around the existing native instance instead of configuring
 * twice. Guarded by ProGuard keep rules on `com.revenuecat.purchases.**`.
 * If a future KMP version adds first-class Galaxy support, replace this with
 * the official call and delete the bridge.
 */
private fun attachKmpWrapperToNativeSingleton() {
    try {
        val kmpClass = Class.forName("com.revenuecat.purchases.kmp.Purchases")
        val constructor = kmpClass.getDeclaredConstructor(Purchases::class.java)
        constructor.isAccessible = true
        val wrapper = constructor.newInstance(Purchases.sharedInstance)
        val field = kmpClass.getDeclaredField("_sharedInstance")
        field.isAccessible = true
        field.set(null, wrapper)
        Log.i("AbbeysBilling", "KMP wrapper attached to native Galaxy-configured SDK")
    } catch (t: Throwable) {
        // Surface loudly: Galaxy billing would otherwise appear unconfigured.
        Log.e("AbbeysBilling", "Failed to attach KMP wrapper to native SDK", t)
        throw IllegalStateException(
            "Galaxy billing bridge failed — KMP internals changed? ${t.message}", t,
        )
    }
}
