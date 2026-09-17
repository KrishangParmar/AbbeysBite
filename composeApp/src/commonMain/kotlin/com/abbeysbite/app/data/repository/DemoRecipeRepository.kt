package com.abbeysbite.app.data.repository

import com.abbeysbite.app.ai.RecipeSearchIntent
import com.abbeysbite.app.core.util.AppError
import com.abbeysbite.app.core.util.AppResult
import com.abbeysbite.app.data.demo.DemoData
import com.abbeysbite.app.data.model.FeedSection
import com.abbeysbite.app.data.model.Recipe
import com.abbeysbite.app.data.model.RecipeDetail
import com.abbeysbite.app.data.model.RecipeStatus
import com.abbeysbite.app.domain.Validators
import com.russhwolf.settings.Settings
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json

/**
 * In-memory + settings-persisted recipe repository for demo mode.
 * Community content comes from [DemoData]; the user's own creations, likes
 * and saves persist locally so every flow is genuinely usable offline.
 */
class DemoRecipeRepository(
    private val settings: Settings,
    private val auth: AuthRepository,
) : RecipeRepository {

    private companion object {
        const val KEY_MY_RECIPES = "demo_my_recipes"
        const val KEY_MY_DETAILS = "demo_my_recipe_details"
        const val KEY_LIKED = "demo_liked_recipe_ids"
        const val KEY_SAVED = "demo_saved_recipe_ids"
    }

    private val json = Json { ignoreUnknownKeys = true }

    private var counter = 0

    private fun myRecipesStored(): MutableList<Recipe> =
        settings.getStringOrNull(KEY_MY_RECIPES)?.let {
            runCatching { json.decodeFromString<List<Recipe>>(it) }.getOrNull()
        }?.toMutableList() ?: mutableListOf()

    private fun myDetailsStored(): MutableMap<String, RecipeDetail> =
        settings.getStringOrNull(KEY_MY_DETAILS)?.let {
            runCatching { json.decodeFromString<Map<String, RecipeDetail>>(it) }.getOrNull()
        }?.toMutableMap() ?: mutableMapOf()

    private fun idSet(key: String): MutableSet<String> =
        settings.getStringOrNull(key)?.split(",")?.filter { it.isNotBlank() }?.toMutableSet()
            ?: mutableSetOf()

    private fun persistIdSet(key: String, ids: Set<String>) =
        settings.putString(key, ids.joinToString(","))

    private fun allPublished(): List<Recipe> =
        DemoData.recipes + myRecipesStored().filter { it.status == RecipeStatus.PUBLISHED }

    private fun decorated(recipe: Recipe): Recipe {
        val liked = recipe.id in idSet(KEY_LIKED)
        return recipe.copy(likeCount = recipe.likeCount + if (liked) 1 else 0)
    }

    override suspend fun feed(section: FeedSection, page: Int): AppResult<List<Recipe>> {
        delay(350) // simulate network for realistic loading states
        if (page > 0) return AppResult.Success(emptyList())
        val all = allPublished()
        val result = when (section) {
            FeedSection.FOR_YOU -> all.shuffled(kotlin.random.Random(42))
            FeedSection.TRENDING -> all.sortedByDescending { it.likeCount }
            FeedSection.QUICK -> all.filter { it.totalMinutes <= 15 }
            FeedSection.BUDGET -> all.filter { "budget" in it.tags }
            FeedSection.VEGETARIAN -> all.filter { "vegetarian" in it.dietTags }
            FeedSection.NEW -> all.sortedByDescending { it.createdAt ?: "" }
        }
        return AppResult.Success(result.map(::decorated))
    }

    override suspend fun detail(recipeId: String): AppResult<RecipeDetail> {
        delay(250)
        val mine = myDetailsStored()[recipeId]
        val detail = mine ?: DemoData.recipes.find { it.id == recipeId }?.let { DemoData.detailFor(it) }
            ?: return AppResult.Failure(AppError.NotFound())
        return AppResult.Success(
            detail.copy(
                recipe = decorated(detail.recipe),
                likedByMe = recipeId in idSet(KEY_LIKED),
                savedByMe = recipeId in idSet(KEY_SAVED),
            )
        )
    }

    override suspend fun myRecipes(): AppResult<List<Recipe>> =
        AppResult.Success(myRecipesStored())

    override suspend fun savedRecipes(): AppResult<List<Recipe>> {
        val saved = idSet(KEY_SAVED)
        return AppResult.Success(allPublished().filter { it.id in saved }.map(::decorated))
    }

    override suspend fun publish(draft: RecipeDraft): AppResult<Recipe> {
        val validation = Validators.validateRecipeForPublish(
            draft.recipe, draft.ingredients, draft.steps,
            hasCoverImage = draft.coverImageBytes != null || draft.recipe.coverImageUrl != null,
        )
        if (!validation.isValid) {
            return AppResult.Failure(AppError.Validation(validation.errors.first()))
        }
        delay(500)
        val uid = auth.currentUserId ?: return AppResult.Failure(AppError.Unauthorized())
        val id = draft.recipe.id.ifBlank { "my-recipe-${counter++}-${draft.recipe.title.hashCode()}" }
        val recipe = draft.recipe.copy(
            id = id,
            authorId = uid,
            status = RecipeStatus.PUBLISHED,
            createdAt = draft.recipe.createdAt ?: "2026-01-01T00:00:00Z",
        )
        val recipes = myRecipesStored().apply {
            removeAll { it.id == id }
            add(0, recipe)
        }
        val details = myDetailsStored().apply {
            this[id] = RecipeDetail(
                recipe = recipe,
                ingredients = draft.ingredients.mapIndexed { i, ing ->
                    ing.copy(id = "$id-ing-$i", recipeId = id, sortOrder = i)
                },
                steps = draft.steps.mapIndexed { i, step ->
                    step.copy(id = "$id-step-$i", recipeId = id, stepNumber = i + 1)
                },
            )
        }
        settings.putString(KEY_MY_RECIPES, json.encodeToString(recipes.toList()))
        settings.putString(KEY_MY_DETAILS, json.encodeToString(details.toMap()))
        return AppResult.Success(recipe)
    }

    override suspend fun unpublish(recipeId: String): AppResult<Unit> =
        updateMine(recipeId) { it.copy(status = RecipeStatus.DRAFT) }

    override suspend fun delete(recipeId: String): AppResult<Unit> {
        val recipes = myRecipesStored().apply { removeAll { it.id == recipeId } }
        val details = myDetailsStored().apply { remove(recipeId) }
        settings.putString(KEY_MY_RECIPES, json.encodeToString(recipes.toList()))
        settings.putString(KEY_MY_DETAILS, json.encodeToString(details.toMap()))
        return AppResult.Success(Unit)
    }

    private fun updateMine(recipeId: String, transform: (Recipe) -> Recipe): AppResult<Unit> {
        val recipes = myRecipesStored()
        val index = recipes.indexOfFirst { it.id == recipeId }
        if (index < 0) return AppResult.Failure(AppError.NotFound())
        recipes[index] = transform(recipes[index])
        settings.putString(KEY_MY_RECIPES, json.encodeToString(recipes.toList()))
        return AppResult.Success(Unit)
    }

    override suspend fun setLiked(recipeId: String, liked: Boolean): AppResult<Boolean> {
        val ids = idSet(KEY_LIKED)
        if (liked) ids.add(recipeId) else ids.remove(recipeId)
        persistIdSet(KEY_LIKED, ids)
        return AppResult.Success(liked)
    }

    override suspend fun setSaved(recipeId: String, saved: Boolean): AppResult<Boolean> {
        val ids = idSet(KEY_SAVED)
        if (saved) ids.add(recipeId) else ids.remove(recipeId)
        persistIdSet(KEY_SAVED, ids)
        return AppResult.Success(saved)
    }

    override suspend fun search(query: String): AppResult<List<Recipe>> {
        delay(200)
        return AppResult.Success(
            allPublished().filter { RecipeFiltering.matchesKeyword(it, query) }.map(::decorated)
        )
    }

    override suspend fun searchByIntent(intent: RecipeSearchIntent): AppResult<List<Recipe>> {
        delay(250)
        val results = allPublished().filter { recipe ->
            val detail = myDetailsStored()[recipe.id]
                ?: DemoData.recipes.find { it.id == recipe.id }?.let { DemoData.detailFor(it) }
            RecipeFiltering.matchesIntent(
                detail?.ingredients?.map { it.name } ?: emptyList(),
                recipe,
                intent,
            )
        }
        return AppResult.Success(results.map(::decorated))
    }
}
