package com.abbeysbite.app.billing

import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * DEBUG-ONLY premium override for testing gated features without a store
 * purchase. Never constructed in release builds (guarded by PlatformInfo.isDebug
 * at the DI layer) and never fakes an actual payment flow — it only flips the
 * local entitlement snapshot.
 */
class DebugDevSettings(private val settings: Settings) {
    private companion object {
        const val KEY = "debug_force_premium"
        const val KEY_MOCK_AI = "debug_use_mock_ai"
    }

    private val _forcePremium = MutableStateFlow(settings.getBoolean(KEY, false))
    val forcePremium: StateFlow<Boolean> = _forcePremium

    fun setForcePremium(enabled: Boolean) {
        settings.putBoolean(KEY, enabled)
        _forcePremium.value = enabled
    }

    /**
     * DEBUG-ONLY: route AI calls to the deterministic mock instead of the
     * on-device model (UI work on machines without the 3.4 GB model). Never
     * consulted in release builds; default OFF so local Gemma is always the
     * honest default.
     */
    private val _useMockAi = MutableStateFlow(settings.getBoolean(KEY_MOCK_AI, false))
    val useMockAi: StateFlow<Boolean> = _useMockAi

    fun setUseMockAi(enabled: Boolean) {
        settings.putBoolean(KEY_MOCK_AI, enabled)
        _useMockAi.value = enabled
    }
}

class DebugOverrideBillingManager(
    private val delegate: BillingManager,
    private val devSettings: DebugDevSettings,
    scope: CoroutineScope,
) : BillingManager {

    override val premiumState: StateFlow<PremiumState> =
        combine(delegate.premiumState, devSettings.forcePremium) { real, forced ->
            if (forced) PremiumState(isPremium = true, loading = false) else real
        }.stateIn(scope, SharingStarted.Eagerly, delegate.premiumState.value)

    override suspend fun refresh() = delegate.refresh()
    override fun loginUser(appUserId: String) = delegate.loginUser(appUserId)
    override fun logoutUser() = delegate.logoutUser()
}
