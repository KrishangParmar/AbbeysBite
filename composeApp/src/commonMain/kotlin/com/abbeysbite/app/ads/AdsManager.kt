package com.abbeysbite.app.ads

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.abbeysbite.app.billing.BillingManager
import com.abbeysbite.app.core.config.AppSecrets
import com.abbeysbite.app.platform.PlatformInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Monetization ads policy, shared across platforms:
 *
 *  - PREMIUM (`abbeysbite_premium`) → zero ads, everywhere.
 *  - FREE → tasteful native placements inside Community ONLY. Never in the
 *    camera flow, meal analysis, meal results, chat, Live or onboarding.
 *  - Release builds serve ads only when real AdMob ids are configured
 *    (PENDING → ads fully disabled rather than test ids shipping).
 *  - Ad lifecycle/revenue events feed analytics + RevenueCat ad tracking on
 *    Android (see AndroidAdsManager).
 */
interface AdsManager {
    /** True when the current user should see ads at all. */
    val adsEnabled: StateFlow<Boolean>

    /** Platform SDK init (no-op when disabled/unsupported). */
    fun initialize()

    /** Lifecycle hooks from platform ad views (analytics + RC tracking). */
    fun onAdEvent(event: AdEvent, detail: String? = null)
}

enum class AdEvent { LOADED, IMPRESSION, OPENED, FAILED, REVENUE }

/** Shared policy half of the manager; platform impls add SDK specifics. */
abstract class BaseAdsManager(
    billingManager: BillingManager,
    protected val platformInfo: PlatformInfo,
    scope: CoroutineScope,
) : AdsManager {

    protected val unitIdsConfigured: Boolean =
        AppSecrets.admobNativeCommunityAdUnitId.isNotBlank() &&
            AppSecrets.admobNativeCommunityAdUnitId != "PENDING"

    /** Debug builds may use Google's official test ids; release never does. */
    protected val servingPossible: Boolean =
        platformInfo.isDebug || unitIdsConfigured

    override val adsEnabled: StateFlow<Boolean> =
        billingManager.premiumState
            .map { premium -> !premium.isPremium && servingPossible && platformSupported }
            .stateIn(scope, SharingStarted.Eagerly, false)

    protected abstract val platformSupported: Boolean
}

/** Community feed native ad slot. Renders nothing when ads are disabled. */
@Composable
expect fun CommunityNativeAdCard(modifier: Modifier)
