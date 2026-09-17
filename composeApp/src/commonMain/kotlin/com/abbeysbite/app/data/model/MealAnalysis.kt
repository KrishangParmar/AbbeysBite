package com.abbeysbite.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Nutrient presence status. Deliberately non-judgmental: a meal is never
 * "bad" — a nutrient is simply present, could be added, or we're not sure.
 */
@Serializable
enum class NutrientStatus {
    @SerialName("present")
    PRESENT,

    @SerialName("could_add")
    COULD_ADD,

    @SerialName("uncertain")
    UNCERTAIN;

    companion object {
        /** Lenient mapping used when parsing AI output. */
        fun fromRaw(raw: String?): NutrientStatus = when (raw?.trim()?.lowercase()) {
            "present", "yes", "good", "included" -> PRESENT
            "could_add", "couldadd", "could add", "missing", "add", "low", "no" -> COULD_ADD
            else -> UNCERTAIN
        }
    }
}

/** How much effort an addition takes. */
@Serializable
enum class AdditionEffort {
    @SerialName("none")
    NONE,        // open a packet, sprinkle

    @SerialName("low")
    LOW,         // 1–2 minutes

    @SerialName("medium")
    MEDIUM;      // short cooking step

    companion object {
        fun fromRaw(raw: String?): AdditionEffort = when (raw?.trim()?.lowercase()) {
            "none", "zero", "instant" -> NONE
            "medium", "moderate", "some" -> MEDIUM
            else -> LOW
        }
    }
}

@Serializable
enum class AdditionCategory {
    @SerialName("protein")
    PROTEIN,

    @SerialName("fibre")
    FIBRE,

    @SerialName("healthy_fat")
    HEALTHY_FAT,

    @SerialName("general")
    GENERAL;

    companion object {
        fun fromRaw(raw: String?): AdditionCategory = when (raw?.trim()?.lowercase()) {
            "protein" -> PROTEIN
            "fibre", "fiber" -> FIBRE
            "healthy_fat", "healthyfat", "fat", "healthy fat" -> HEALTHY_FAT
            else -> GENERAL
        }
    }
}

@Serializable
data class MealComponent(
    val name: String,
    val confidence: Double = 1.0,
    /** True when the user added/confirmed this component manually. */
    @SerialName("user_added") val userAdded: Boolean = false,
)

@Serializable
data class SuggestedAddition(
    val name: String,
    val category: AdditionCategory = AdditionCategory.GENERAL,
    /** Short, friendly explanation of why this fits THIS meal. */
    val reason: String = "",
    val effort: AdditionEffort = AdditionEffort.LOW,
    /** True when the item (or a close substitute) is in the user's pantry. */
    @SerialName("pantry_match") val pantryMatch: Boolean = false,
    /** Optional swap when the user lacks or dislikes the main suggestion. */
    @SerialName("optional_substitution") val optionalSubstitution: String? = null,
)

/**
 * Structured result of analyzing one meal. This is the single AI contract —
 * providers must return exactly this shape (see [com.abbeysbite.app.ai.AiProvider]).
 */
@Serializable
data class MealAnalysis(
    @SerialName("detected_meal_name") val detectedMealName: String,
    val confidence: Double = 0.0,
    val components: List<MealComponent> = emptyList(),
    @SerialName("protein_status") val proteinStatus: NutrientStatus = NutrientStatus.UNCERTAIN,
    @SerialName("protein_sources") val proteinSources: List<String> = emptyList(),
    @SerialName("fibre_status") val fibreStatus: NutrientStatus = NutrientStatus.UNCERTAIN,
    @SerialName("fibre_sources") val fibreSources: List<String> = emptyList(),
    @SerialName("healthy_fat_status") val healthyFatStatus: NutrientStatus = NutrientStatus.UNCERTAIN,
    @SerialName("healthy_fat_sources") val healthyFatSources: List<String> = emptyList(),
    @SerialName("suggested_additions") val suggestedAdditions: List<SuggestedAddition> = emptyList(),
    /** Friendly, shame-free note about the plate. Never a judgment. */
    val notes: String = "",
)

/** A manual correction the user applies when recognition is off. */
@Serializable
data class MealCorrection(
    @SerialName("renamed_meal") val renamedMeal: String? = null,
    @SerialName("added_components") val addedComponents: List<String> = emptyList(),
    @SerialName("removed_components") val removedComponents: List<String> = emptyList(),
)
