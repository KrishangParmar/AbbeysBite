package com.abbeysbite.app.analytics

/**
 * Minimal, privacy-conscious analytics abstraction. Events carry only
 * product-level names and coarse properties — never meal photos, chat
 * content, or other private user data.
 */
interface Analytics {
    fun track(event: String, properties: Map<String, String> = emptyMap())
    fun setUserId(userId: String?)
}

/** Well-known event names, centralized to keep dashboards consistent. */
object AnalyticsEvents {
    const val ONBOARDING_COMPLETED = "onboarding_completed"
    const val MEAL_SCAN_STARTED = "meal_scan_started"
    const val MEAL_SCAN_COMPLETED = "meal_scan_completed"
    const val SUGGESTION_OPENED = "suggestion_opened"
    const val CHAT_MESSAGE_SENT = "chat_message_sent"
    const val VOICE_STARTED = "voice_started"
    const val MEAL_LOGGED = "meal_logged"
    const val RECIPE_VIEWED = "recipe_viewed"
    const val RECIPE_PUBLISHED = "recipe_published"
    const val RECIPE_SAVED = "recipe_saved"
    const val FRIEND_REQUEST_SENT = "friend_request_sent"
    const val WEEKLY_INSIGHT_VIEWED = "weekly_insight_viewed"
    const val PAYWALL_VIEWED = "paywall_viewed"
    const val TRIAL_STARTED = "trial_started"
    const val PURCHASE_COMPLETED = "purchase_completed"
}

/**
 * Default implementation: logs in debug, no-ops in release. Swap for a real
 * vendor SDK behind this interface without touching call sites.
 */
class LoggingAnalytics(private val isDebug: Boolean) : Analytics {
    override fun track(event: String, properties: Map<String, String>) {
        if (isDebug) println("[analytics] $event ${if (properties.isEmpty()) "" else properties}")
    }

    override fun setUserId(userId: String?) {
        if (isDebug) println("[analytics] user=${userId?.take(8) ?: "signed-out"}")
    }
}
