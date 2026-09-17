package com.abbeysbite.app.features.paywall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abbeysbite.app.analytics.Analytics
import com.abbeysbite.app.analytics.AnalyticsEvents
import com.abbeysbite.app.billing.BillingManager
import com.abbeysbite.app.core.config.AppConfig
import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.ktx.awaitOfferings
import com.revenuecat.purchases.kmp.ktx.awaitPurchase
import com.revenuecat.purchases.kmp.ktx.awaitRestore
import com.revenuecat.purchases.kmp.models.Package
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PaywallPackage(
    val rcPackage: Package,
    val title: String,
    val priceLabel: String,
    val periodLabel: String,
    val hasFreeTrial: Boolean,
    val isAnnual: Boolean,
)

data class PaywallUiState(
    val loading: Boolean = true,
    val packages: List<PaywallPackage> = emptyList(),
    val selectedIndex: Int = 0,
    val purchasing: Boolean = false,
    val restoring: Boolean = false,
    val error: String? = null,
    val purchased: Boolean = false,
    /** True when billing is unavailable (no credentials / store unreachable). */
    val unavailable: Boolean = false,
)

class PaywallViewModel(
    private val billingManager: BillingManager,
    private val analytics: Analytics,
) : ViewModel() {

    private val _state = MutableStateFlow(PaywallUiState())
    val state: StateFlow<PaywallUiState> = _state.asStateFlow()

    init {
        analytics.track(AnalyticsEvents.PAYWALL_VIEWED)
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            if (billingManager.premiumState.value.unavailableReason == "billing_not_configured") {
                _state.update { it.copy(loading = false, unavailable = true) }
                return@launch
            }
            runCatching {
                val offerings = Purchases.sharedInstance.awaitOfferings()
                val offering = offerings.getOffering(AppConfig.DEFAULT_OFFERING) ?: offerings.current
                val packages = offering?.availablePackages.orEmpty().map { pkg ->
                    val product = pkg.storeProduct
                    val period = product.period
                    val isAnnual = period?.unit?.name?.contains("YEAR", true) == true
                    PaywallPackage(
                        rcPackage = pkg,
                        title = if (isAnnual) "Annual" else "Monthly",
                        priceLabel = product.price.formatted,
                        periodLabel = if (isAnnual) "per year" else "per month",
                        hasFreeTrial = product.subscriptionOptions?.freeTrial != null,
                        isAnnual = isAnnual,
                    )
                }.sortedByDescending { it.isAnnual }
                _state.update {
                    it.copy(
                        loading = false,
                        packages = packages,
                        unavailable = packages.isEmpty(),
                    )
                }
            }.onFailure {
                _state.update { it.copy(loading = false, unavailable = true) }
            }
        }
    }

    fun select(index: Int) = _state.update { it.copy(selectedIndex = index, error = null) }

    fun purchase() {
        val pkg = _state.value.packages.getOrNull(_state.value.selectedIndex) ?: return
        _state.update { it.copy(purchasing = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val result = Purchases.sharedInstance.awaitPurchase(pkg.rcPackage)
                val active = result.customerInfo.entitlements.active
                    .containsKey(AppConfig.ENTITLEMENT_PREMIUM)
                if (active) {
                    if (pkg.hasFreeTrial) analytics.track(AnalyticsEvents.TRIAL_STARTED)
                    analytics.track(AnalyticsEvents.PURCHASE_COMPLETED)
                    billingManager.refresh()
                    _state.update { it.copy(purchasing = false, purchased = true) }
                } else {
                    _state.update {
                        it.copy(purchasing = false, error = "Purchase completed but entitlement isn’t active yet. Try Restore in a moment.")
                    }
                }
            }.onFailure { e ->
                val message = e.message.orEmpty()
                _state.update {
                    it.copy(
                        purchasing = false,
                        // User cancellation isn't an error worth shouting about.
                        error = if (message.contains("cancel", true)) null
                        else "The purchase didn’t go through. You haven’t been charged — please try again.",
                    )
                }
            }
        }
    }

    fun restore() {
        _state.update { it.copy(restoring = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val info = Purchases.sharedInstance.awaitRestore()
                val active = info.entitlements.active.containsKey(AppConfig.ENTITLEMENT_PREMIUM)
                billingManager.refresh()
                _state.update {
                    it.copy(
                        restoring = false,
                        purchased = active,
                        error = if (active) null else "No previous purchases found for this account.",
                    )
                }
            }.onFailure {
                _state.update {
                    it.copy(restoring = false, error = "Restore didn’t work. Check your connection and try again.")
                }
            }
        }
    }
}
