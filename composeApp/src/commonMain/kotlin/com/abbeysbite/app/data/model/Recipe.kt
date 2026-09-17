package com.abbeysbite.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class RecipeDifficulty {
    @SerialName("easy") EASY,
    @SerialName("medium") MEDIUM,
    @SerialName("involved") INVOLVED;

    val label: String
        get() = when (this) {
            EASY -> "Easy"
            MEDIUM -> "Medium"
            INVOLVED -> "Involved"
        }
}

@Serializable
enum class RecipeStatus {
    @SerialName("draft") DRAFT,
    @SerialName("published") PUBLISHED,
    @SerialName("under_review") UNDER_REVIEW,
    @SerialName("removed") REMOVED,
}

@Serializable
data class RecipeIngredient(
    val id: String = "",
    @SerialName("recipe_id") val recipeId: String = "",
    val name: String,
    val quantity: String? = null,
    val unit: String? = null,
    @SerialName("sort_order") val sortOrder: Int = 0,
    val optional: Boolean = false,
    val substitution: String? = null,
)

@Serializable
data class RecipeStep(
    val id: String = "",
    @SerialName("recipe_id") val recipeId: String = "",
    @SerialName("step_number") val stepNumber: Int,
    val instruction: String,
)

@Serializable
data class Recipe(
    val id: String = "",
    @SerialName("author_id") val authorId: String = "",
    val title: String,
    val description: String = "",
    @SerialName("cover_image_url") val coverImageUrl: String? = null,
    @SerialName("prep_minutes") val prepMinutes: Int = 0,
    @SerialName("cook_minutes") val cookMinutes: Int = 0,
    val difficulty: RecipeDifficulty = RecipeDifficulty.EASY,
    val servings: Int = 1,
    val cuisine: String? = null,
    val tags: List<String> = emptyList(),
    @SerialName("diet_tags") val dietTags: List<String> = emptyList(),
    @SerialName("youtube_url") val youtubeUrl: String? = null,
    val tips: String? = null,
    val status: RecipeStatus = RecipeStatus.DRAFT,
    @SerialName("like_count") val likeCount: Int = 0,
    @SerialName("save_count") val saveCount: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
) {
    val totalMinutes: Int get() = prepMinutes + cookMinutes
}

/** Recipe with its relations, as shown on the detail screen. */
@Serializable
data class RecipeDetail(
    val recipe: Recipe,
    val ingredients: List<RecipeIngredient> = emptyList(),
    val steps: List<RecipeStep> = emptyList(),
    val author: Profile? = null,
    @SerialName("liked_by_me") val likedByMe: Boolean = false,
    @SerialName("saved_by_me") val savedByMe: Boolean = false,
)

/** Sections shown on the community feed. */
enum class FeedSection(val label: String) {
    FOR_YOU("For You"),
    TRENDING("Trending"),
    QUICK("Quick"),
    BUDGET("Budget"),
    VEGETARIAN("Vegetarian"),
    NEW("New"),
}
