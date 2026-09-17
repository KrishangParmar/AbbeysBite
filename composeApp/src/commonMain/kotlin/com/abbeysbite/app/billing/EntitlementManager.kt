package com.abbeysbite.app.billing

import com.abbeysbite.app.core.config.AppConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.LocalDate

/** Premium entitlement snapshot used across the app. */
data class PremiumState(
    val isPremium: Boolean = false,
    /** True while the initial entitlement fetch is in flight. */
    val loading: Boolean = true,
    /** Non-null when RevenueCat could not be reached; features degrade gracefully. */
    val unavailableReason: String? = null,
)

/**
 * Free-tier usage gating. Counts are stored per-day; premium bypasses all
 * limits. Pure logic so it is fully testable — persistence is injected.
 */
class FreeTierLimiter(
    private val readCount: (key: String) -> Int,
    private val writeCount: (key: String, value: Int) -> Unit,
) {
    fun analysesUsedToday(today: LocalDate): Int = readCount(key("analysis", today))

    fun canAnalyze(isPremium: Boolean, today: LocalDate): Boolean =
        isPremium || analysesUsedToday(today) < AppConfig.FreeTier.DAILY_MEAL_ANALYSES

    fun recordAnalysis(today: LocalDate) {
        val k = key("analysis", today)
        writeCount(k, readCount(k) + 1)
    }

    fun analysesRemaining(isPremium: Boolean, today: LocalDate): Int? =
        if (isPremium) null
        else (AppConfig.FreeTier.DAILY_MEAL_ANALYSES - analysesUsedToday(today)).coerceAtLeast(0)

    fun chatMessagesUsedToday(today: LocalDate): Int = readCount(key("chat", today))

    fun canChat(isPremium: Boolean, today: LocalDate): Boolean =
        isPremium || chatMessagesUsedToday(today) < AppConfig.FreeTier.DAILY_CHAT_MESSAGES

    fun recordChatMessage(today: LocalDate) {
        val k = key("chat", today)
        writeCount(k, readCount(k) + 1)
    }

    fun canUseVoice(isPremium: Boolean): Boolean = isPremium || AppConfig.FreeTier.VOICE_ENABLED

    fun canUsePantryAwareAi(isPremium: Boolean): Boolean =
        isPremium || AppConfig.FreeTier.PANTRY_AWARE_AI

    private fun key(feature: String, day: LocalDate) = "limit_${feature}_$day"
}

/**
 * Abstraction over the billing SDK (RevenueCat KMP). The UI observes
 * [premiumState]; purchase flows live in the paywall feature.
 */
interface BillingManager {
    val premiumState: StateFlow<PremiumState>

    /** Refresh customer info (e.g. on app start / after sign-in). */
    suspend fun refresh()

    fun loginUser(appUserId: String)
    fun logoutUser()
}

/** No-credential fallback: everything free-tier, purchases unavailable. */
class UnconfiguredBillingManager : BillingManager {
    private val _state = MutableStateFlow(
        PremiumState(isPremium = false, loading = false, unavailableReason = "billing_not_configured")
    )
    override val premiumState: StateFlow<PremiumState> = _state.asStateFlow()
    override suspend fun refresh() = Unit
    override fun loginUser(appUserId: String) = Unit
    override fun logoutUser() = Unit
}
