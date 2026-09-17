package com.abbeysbite.app.navigation

import kotlinx.serialization.Serializable

/** Type-safe navigation destinations. */
sealed interface Route {

    @Serializable
    data object Splash : Route

    @Serializable
    data object Onboarding : Route

    @Serializable
    data object Auth : Route

    /** Bottom-tab shell containing the four primary tabs. */
    @Serializable
    data object Main : Route

    @Serializable
    data class MealAnalysis(val entryId: String? = null) : Route

    @Serializable
    data class MealChat(val analysisId: String) : Route

    @Serializable
    data class VoiceMode(val analysisId: String) : Route

    @Serializable
    data class RecipeDetail(val recipeId: String) : Route

    @Serializable
    data class RecipeEditor(val recipeId: String? = null) : Route

    @Serializable
    data object CommunitySearch : Route

    @Serializable
    data class UserProfile(val userId: String) : Route

    @Serializable
    data object Friends : Route

    @Serializable
    data object Pantry : Route

    @Serializable
    data object Preferences : Route

    @Serializable
    data object NotificationSettings : Route

    @Serializable
    data object PrivacySettings : Route

    @Serializable
    data object Paywall : Route

    @Serializable
    data object SavedRecipes : Route

    @Serializable
    data object MyRecipes : Route

    @Serializable
    data object Help : Route

    /** kindName = LegalContent.Kind name (PRIVACY / TERMS / GUIDELINES). */
    @Serializable
    data class Legal(val kindName: String) : Route

    @Serializable
    data object DeleteAccount : Route
}

/** The four primary bottom tabs. */
enum class MainTab(val label: String) {
    IMPROVE("Improve"),
    COMMUNITY("Community"),
    JOURNAL("Journal"),
    YOU("You"),
}
