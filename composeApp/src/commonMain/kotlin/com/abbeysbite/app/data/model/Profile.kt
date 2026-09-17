package com.abbeysbite.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val bio: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
) {
    val displayOrUsername: String get() = displayName?.takeIf { it.isNotBlank() } ?: username
}

@Serializable
enum class DietPreference {
    @SerialName("vegetarian") VEGETARIAN,
    @SerialName("vegan") VEGAN,
    @SerialName("omnivore") OMNIVORE,
    @SerialName("no_preference") NO_PREFERENCE;

    val label: String
        get() = when (this) {
            VEGETARIAN -> "Vegetarian"
            VEGAN -> "Vegan"
            OMNIVORE -> "Omnivore"
            NO_PREFERENCE -> "No preference"
        }
}

@Serializable
enum class CookingSetup {
    @SerialName("full_kitchen") FULL_KITCHEN,
    @SerialName("microwave_only") MICROWAVE_ONLY,
    @SerialName("hostel_dorm") HOSTEL_DORM,
    @SerialName("no_cooking") NO_COOKING,
    @SerialName("air_fryer") AIR_FRYER;

    val label: String
        get() = when (this) {
            FULL_KITCHEN -> "Full kitchen"
            MICROWAVE_ONLY -> "Microwave only"
            HOSTEL_DORM -> "Hostel / dorm"
            NO_COOKING -> "No cooking"
            AIR_FRYER -> "Air fryer"
        }
}

@Serializable
enum class EatingGoal {
    @SerialName("satisfying_meals") SATISFYING_MEALS,
    @SerialName("easier_ideas") EASIER_IDEAS,
    @SerialName("consistency") CONSISTENCY,
    @SerialName("discover_recipes") DISCOVER_RECIPES,
    @SerialName("understand_patterns") UNDERSTAND_PATTERNS;

    val label: String
        get() = when (this) {
            SATISFYING_MEALS -> "More satisfying meals"
            EASIER_IDEAS -> "Easier meal ideas"
            CONSISTENCY -> "Better consistency"
            DISCOVER_RECIPES -> "Discover recipes"
            UNDERSTAND_PATTERNS -> "Understand my eating patterns"
        }
}

@Serializable
data class UserPreferences(
    @SerialName("user_id") val userId: String = "",
    val goals: List<EatingGoal> = emptyList(),
    @SerialName("diet_preference") val dietPreference: DietPreference = DietPreference.NO_PREFERENCE,
    /** Free-text foods to avoid, allergies, dislikes. */
    @SerialName("avoid_foods") val avoidFoods: List<String> = emptyList(),
    @SerialName("cooking_setup") val cookingSetup: CookingSetup = CookingSetup.FULL_KITCHEN,
    @SerialName("onboarding_completed") val onboardingCompleted: Boolean = false,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class PantryItem(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    val name: String,
    val category: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)
