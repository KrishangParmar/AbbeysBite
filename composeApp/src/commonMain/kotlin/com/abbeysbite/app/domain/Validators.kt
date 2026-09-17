package com.abbeysbite.app.domain

import com.abbeysbite.app.core.config.AppConfig
import com.abbeysbite.app.data.model.Recipe
import com.abbeysbite.app.data.model.RecipeIngredient
import com.abbeysbite.app.data.model.RecipeStep

/** Validation for user-generated content. Pure logic, fully testable. */
object Validators {

    // ------------------------------------------------------------------ YouTube

    private val youtubeIdRegex = Regex("^[A-Za-z0-9_-]{11}$")

    /**
     * Extracts the video id from any standard YouTube URL form
     * (watch?v=, youtu.be/, shorts/, embed/). Returns null when invalid.
     */
    fun youtubeVideoId(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val trimmed = url.trim()
        val candidates = listOf(
            Regex("[?&]v=([A-Za-z0-9_-]{11})"),
            Regex("youtu\\.be/([A-Za-z0-9_-]{11})"),
            Regex("youtube\\.com/shorts/([A-Za-z0-9_-]{11})"),
            Regex("youtube\\.com/embed/([A-Za-z0-9_-]{11})"),
        )
        if (!trimmed.contains("youtube.com") && !trimmed.contains("youtu.be")) return null
        for (regex in candidates) {
            regex.find(trimmed)?.groupValues?.get(1)?.let { id ->
                if (youtubeIdRegex.matches(id)) return id
            }
        }
        return null
    }

    fun isValidYoutubeUrl(url: String?): Boolean = youtubeVideoId(url) != null

    // ------------------------------------------------------------------ Username

    private val usernameRegex = Regex("^[a-z0-9_]{3,24}$")

    fun isValidUsername(username: String): Boolean = usernameRegex.matches(username)

    fun usernameError(username: String): String? = when {
        username.length < 3 -> "Username needs at least 3 characters."
        username.length > 24 -> "Username can be at most 24 characters."
        !usernameRegex.matches(username) ->
            "Use lowercase letters, numbers and underscores only."
        else -> null
    }

    // ------------------------------------------------------------------ Recipe

    data class RecipeValidation(val errors: List<String>) {
        val isValid: Boolean get() = errors.isEmpty()
    }

    fun validateRecipeForPublish(
        recipe: Recipe,
        ingredients: List<RecipeIngredient>,
        steps: List<RecipeStep>,
        hasCoverImage: Boolean,
    ): RecipeValidation {
        val errors = buildList {
            if (recipe.title.trim().length < 3) add("Give your recipe a title (at least 3 characters).")
            if (recipe.title.length > 90) add("Title is too long (max 90 characters).")
            if (recipe.description.length > 600) add("Description is too long (max 600 characters).")
            if (!hasCoverImage) add("Add a cover photo so people can see the dish.")
            if (ingredients.none { it.name.isNotBlank() }) add("List at least one ingredient.")
            if (ingredients.size > AppConfig.Community.MAX_INGREDIENTS) {
                add("Too many ingredients (max ${AppConfig.Community.MAX_INGREDIENTS}).")
            }
            if (steps.none { it.instruction.isNotBlank() }) add("Add at least one step.")
            if (steps.size > AppConfig.Community.MAX_STEPS) {
                add("Too many steps (max ${AppConfig.Community.MAX_STEPS}).")
            }
            if (recipe.servings !in 1..99) add("Servings should be between 1 and 99.")
            if (recipe.prepMinutes !in 0..24 * 60) add("Prep time looks off.")
            if (recipe.cookMinutes !in 0..24 * 60) add("Cook time looks off.")
            if (recipe.tags.size > AppConfig.Community.MAX_RECIPE_TAGS) {
                add("Too many tags (max ${AppConfig.Community.MAX_RECIPE_TAGS}).")
            }
            if (!recipe.youtubeUrl.isNullOrBlank() && !isValidYoutubeUrl(recipe.youtubeUrl)) {
                add("That YouTube link doesn’t look valid.")
            }
        }
        return RecipeValidation(errors)
    }
}
