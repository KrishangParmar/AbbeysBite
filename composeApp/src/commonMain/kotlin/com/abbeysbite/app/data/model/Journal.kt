package com.abbeysbite.app.data.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MealType {
    @SerialName("breakfast") BREAKFAST,
    @SerialName("lunch") LUNCH,
    @SerialName("dinner") DINNER,
    @SerialName("snack") SNACK;

    val label: String
        get() = when (this) {
            BREAKFAST -> "Breakfast"
            LUNCH -> "Lunch"
            DINNER -> "Dinner"
            SNACK -> "Snacks"
        }
}

@Serializable
enum class SatisfactionLevel {
    @SerialName("still_hungry") STILL_HUNGRY,
    @SerialName("satisfied") SATISFIED,
    @SerialName("very_satisfied") VERY_SATISFIED;

    val label: String
        get() = when (this) {
            STILL_HUNGRY -> "Still hungry"
            SATISFIED -> "Satisfied"
            VERY_SATISFIED -> "Very satisfied"
        }
}

@Serializable
data class MealEntry(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("meal_type") val mealType: MealType = MealType.SNACK,
    @SerialName("meal_name") val mealName: String = "",
    @SerialName("photo_path") val photoPath: String? = null,
    /** ISO-8601 instant of when the meal was eaten/logged. */
    @SerialName("eaten_at") val eatenAt: String = "",
    /** Local calendar date (user timezone at logging time), for grouping. */
    @SerialName("entry_date") val entryDate: String = "",
    val note: String? = null,
    val satisfaction: SatisfactionLevel? = null,
    val analysis: MealAnalysis? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

/** One day in the rhythm strip. */
data class RhythmDay(
    val date: LocalDate,
    val logged: Boolean,
    val isToday: Boolean,
)

/** Aggregated stats for the nourishment snapshot. */
data class NourishmentSnapshot(
    val mealsLogged: Int = 0,
    val daysWithMeals: Int = 0,
    val proteinPresentCount: Int = 0,
    val fibrePresentCount: Int = 0,
    val healthyFatPresentCount: Int = 0,
    /** Distinct plant-food components seen across the period. */
    val plantVariety: Int = 0,
)

@Serializable
data class WeeklyInsight(
    val observations: List<String> = emptyList(),
    val ideas: List<String> = emptyList(),
    @SerialName("week_start") val weekStart: String = "",
    @SerialName("generated_at") val generatedAt: String = "",
)
