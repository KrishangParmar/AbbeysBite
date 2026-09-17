package com.abbeysbite.app.core.config

/**
 * Central product configuration. Free-tier limits, entitlement identifiers and
 * product ids all live here so they can be tuned without hunting through
 * feature code.
 */
object AppConfig {

    /**
     * RevenueCat entitlement identifier that unlocks premium features.
     * Matches the configured RevenueCat project (`abbeysbite_premium`);
     * overridable via REVENUECAT_ENTITLEMENT_ID per environment. The paywall
     * resolves monthly/yearly PACKAGES from the offering dynamically — raw
     * product ids are intentionally not referenced by UI/business logic.
     */
    val ENTITLEMENT_PREMIUM: String =
        AppSecrets.revenuecatEntitlementId.ifBlank { "abbeysbite_premium" }

    /** RevenueCat offering shown by default on the paywall. */
    val DEFAULT_OFFERING: String =
        AppSecrets.revenuecatOfferingId.ifBlank { "default" }

    /** Limits applied when the user does NOT have the premium entitlement. */
    object FreeTier {
        const val DAILY_MEAL_ANALYSES = 3
        const val DAILY_CHAT_MESSAGES = 10
        const val VOICE_ENABLED = false
        const val PANTRY_AWARE_AI = false
        const val ADVANCED_WEEKLY_INSIGHT = false
    }

    object Ai {
        /** Longest we wait for a single analysis before surfacing a friendly timeout. */
        const val ANALYSIS_TIMEOUT_MS = 60_000L
        const val CHAT_TIMEOUT_MS = 45_000L
        /** Max dimension for uploaded meal photos (px) — compressed client-side. */
        const val IMAGE_MAX_DIMENSION = 1280
        const val IMAGE_JPEG_QUALITY = 82
    }

    object Journal {
        /** Days shown in the rhythm strip. */
        const val RHYTHM_DAYS = 7
    }

    object Community {
        const val FEED_PAGE_SIZE = 20
        const val MAX_RECIPE_TAGS = 8
        const val MAX_INGREDIENTS = 40
        const val MAX_STEPS = 30
    }
}
