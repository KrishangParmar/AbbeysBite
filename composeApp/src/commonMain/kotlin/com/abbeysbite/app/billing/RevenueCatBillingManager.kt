package com.abbeysbite.app.billing

import com.abbeysbite.app.core.config.AppConfig
import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.ktx.awaitCustomerInfo
import com.revenuecat.purchases.kmp.ktx.awaitLogIn
import com.revenuecat.purchases.kmp.ktx.awaitLogOut
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * RevenueCat-backed [BillingManager]. Configuration is store-aware (Play /
 * Galaxy / App Store / Test Store) through [configureRevenueCatForStore];
 * the stable RevenueCat App User ID is always the Supabase Auth UUID, set via
 * [loginUser] after authentication and cleared on [logoutUser].
 */
class RevenueCatBillingManager(
    private val apiKey: String,
    private val storeVariant: String,
    private val isDebugBuild: Boolean,
    private val scope: CoroutineScope,
) : BillingManager {

    private val _premiumState = MutableStateFlow(PremiumState(loading = true))
    override val premiumState: StateFlow<PremiumState> = _premiumState.asStateFlow()

    private var configured = false

    fun configure() {
        if (configured || apiKey.isBlank()) {
            if (apiKey.isBlank()) {
                _premiumState.value = PremiumState(
                    isPremium = false, loading = false,
                    unavailableReason = "billing_not_configured",
                )
            }
            return
        }
        runCatching {
            configureRevenueCatForStore(apiKey, storeVariant, isDebugBuild)
            configured = true
        }.onFailure {
            _premiumState.value = PremiumState(
                isPremium = false, loading = false,
                unavailableReason = "billing_init_failed",
            )
        }
        if (configured) {
            scope.launch { refresh() }
        }
    }

    override suspend fun refresh() {
        if (!configured) return
        runCatching {
            val info = Purchases.sharedInstance.awaitCustomerInfo()
            val active = info.entitlements.active.containsKey(AppConfig.ENTITLEMENT_PREMIUM)
            _premiumState.value = PremiumState(isPremium = active, loading = false)
        }.onFailure {
            _premiumState.value = _premiumState.value.copy(
                loading = false,
                unavailableReason = "billing_unreachable",
            )
        }
    }

    override fun loginUser(appUserId: String) {
        if (!configured || appUserId.isBlank()) return
        scope.launch {
            runCatching {
                Purchases.sharedInstance.awaitLogIn(newAppUserID = appUserId)
            }
            refresh()
        }
    }

    override fun logoutUser() {
        if (!configured) return
        scope.launch {
            runCatching { Purchases.sharedInstance.awaitLogOut() }
            // Entitlements must never leak across accounts.
            _premiumState.value = PremiumState(isPremium = false, loading = false)
        }
    }
}
