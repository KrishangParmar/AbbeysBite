package com.abbeysbite.app.data.repository

import com.abbeysbite.app.ai.RecipeSearchIntent
import com.abbeysbite.app.core.util.AppResult
import com.abbeysbite.app.data.model.FeedSection
import com.abbeysbite.app.data.model.Recipe
import com.abbeysbite.app.data.model.RecipeDetail
import com.abbeysbite.app.data.model.RecipeIngredient
import com.abbeysbite.app.data.model.RecipeStep

/** Draft payload used by the recipe editor before/at publish time. */
data class RecipeDraft(
    val recipe: Recipe,
    val ingredients: List<RecipeIngredient>,
    val steps: List<RecipeStep>,
    /** Local image bytes if the user picked a new cover; null keeps existing. */
    val coverImageBytes: ByteArray? = null,
)

interface RecipeRepository {

    suspend fun feed(section: FeedSection, page: Int = 0): AppResult<List<Recipe>>

    suspend fun detail(recipeId: String): AppResult<RecipeDetail>

    suspend fun myRecipes(): AppResult<List<Recipe>>

    suspend fun savedRecipes(): AppResult<List<Recipe>>

    /** Publish (or republish after edit). Validates before writing. */
    suspend fun publish(draft: RecipeDraft): AppResult<Recipe>

    suspend fun unpublish(recipeId: String): AppResult<Unit>

    suspend fun delete(recipeId: String): AppResult<Unit>

    suspend fun setLiked(recipeId: String, liked: Boolean): AppResult<Boolean>

    suspend fun setSaved(recipeId: String, saved: Boolean): AppResult<Boolean>

    /** Keyword search over title/description/tags. */
    suspend fun search(query: String): AppResult<List<Recipe>>

    /** Structured search from AI-parsed natural-language intent. */
    suspend fun searchByIntent(intent: RecipeSearchIntent): AppResult<List<Recipe>>
}

/** Pure filtering used by both implementations (and unit tests). */
object RecipeFiltering {

    fun matchesIntent(detailIngredients: List<String>, recipe: Recipe, intent: RecipeSearchIntent): Boolean {
        if (intent.maxTotalMinutes != null && recipe.totalMinutes > intent.maxTotalMinutes) return false
        if (intent.dietTags.isNotEmpty()) {
            val recipeDiet = recipe.dietTags.map { it.lowercase() }
            if (!intent.dietTags.all { it.lowercase() in recipeDiet }) return false
        }
        if (intent.budget && "budget" !in recipe.tags.map { it.lowercase() }) return false
        if (intent.mustUseIngredients.isNotEmpty()) {
            val haystack = (detailIngredients + recipe.title + recipe.description)
                .joinToString(" ").lowercase()
            if (!intent.mustUseIngredients.any { haystack.contains(it.lowercase()) }) return false
        }
        if (intent.keywords.isNotEmpty()) {
            val haystack = (listOf(recipe.title, recipe.description, recipe.cuisine.orEmpty()) +
                recipe.tags).joinToString(" ").lowercase()
            if (!intent.keywords.any { haystack.contains(it.lowercase()) }) return false
        }
        return true
    }

    fun matchesKeyword(recipe: Recipe, query: String): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return true
        val haystack = (listOf(recipe.title, recipe.description, recipe.cuisine.orEmpty()) +
            recipe.tags + recipe.dietTags).joinToString(" ").lowercase()
        return q.split(Regex("\\s+")).all { haystack.contains(it) }
    }
}
