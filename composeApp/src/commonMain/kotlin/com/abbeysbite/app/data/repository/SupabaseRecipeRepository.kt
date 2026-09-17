package com.abbeysbite.app.data.repository

import com.abbeysbite.app.ai.RecipeSearchIntent
import com.abbeysbite.app.core.config.AppConfig
import com.abbeysbite.app.core.util.AppError
import com.abbeysbite.app.core.util.AppResult
import com.abbeysbite.app.core.util.toAppError
import com.abbeysbite.app.data.model.FeedSection
import com.abbeysbite.app.data.model.Profile
import com.abbeysbite.app.data.model.Recipe
import com.abbeysbite.app.data.model.RecipeDetail
import com.abbeysbite.app.data.model.RecipeStatus
import com.abbeysbite.app.domain.Validators
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class RecipeUpsertDto(
    @SerialName("author_id") val authorId: String,
    val title: String,
    val description: String,
    @SerialName("cover_image_url") val coverImageUrl: String?,
    @SerialName("prep_minutes") val prepMinutes: Int,
    @SerialName("cook_minutes") val cookMinutes: Int,
    @SerialName("total_minutes") val totalMinutes: Int,
    val difficulty: String,
    val servings: Int,
    val cuisine: String?,
    val tags: List<String>,
    @SerialName("diet_tags") val dietTags: List<String>,
    @SerialName("youtube_url") val youtubeUrl: String?,
    val tips: String?,
    val status: String,
)

class SupabaseRecipeRepository(
    private val supabase: SupabaseClient,
    private val auth: AuthRepository,
    private val storage: MediaStorageRepository,
) : RecipeRepository {

    override suspend fun feed(section: FeedSection, page: Int): AppResult<List<Recipe>> = try {
        val pageSize = AppConfig.Community.FEED_PAGE_SIZE
        val fromIndex = (page * pageSize).toLong()
        val toIndex = fromIndex + pageSize - 1
        val list = supabase.from("recipes")
            .select {
                filter {
                    eq("status", "published")
                    when (section) {
                        FeedSection.QUICK -> lte("total_minutes", 15)
                        FeedSection.BUDGET -> contains("tags", listOf("budget"))
                        FeedSection.VEGETARIAN -> contains("diet_tags", listOf("vegetarian"))
                        else -> Unit
                    }
                }
                when (section) {
                    FeedSection.TRENDING -> order("like_count", Order.DESCENDING)
                    FeedSection.NEW -> order("created_at", Order.DESCENDING)
                    else -> order("created_at", Order.DESCENDING)
                }
                range(fromIndex, toIndex)
            }
            .decodeList<Recipe>()
        AppResult.Success(list)
    } catch (e: Exception) {
        AppResult.Failure(e.toAppError())
    }

    override suspend fun detail(recipeId: String): AppResult<RecipeDetail> = try {
        val recipe = supabase.from("recipes")
            .select { filter { eq("id", recipeId) } }
            .decodeList<Recipe>()
            .firstOrNull() ?: return AppResult.Failure(AppError.NotFound())

        val ingredients = supabase.from("recipe_ingredients")
            .select {
                filter { eq("recipe_id", recipeId) }
                order("sort_order", Order.ASCENDING)
            }
            .decodeList<com.abbeysbite.app.data.model.RecipeIngredient>()

        val steps = supabase.from("recipe_steps")
            .select {
                filter { eq("recipe_id", recipeId) }
                order("step_number", Order.ASCENDING)
            }
            .decodeList<com.abbeysbite.app.data.model.RecipeStep>()

        val author = supabase.from("profiles")
            .select { filter { eq("id", recipe.authorId) } }
            .decodeList<Profile>()
            .firstOrNull()

        val uid = auth.currentUserId
        val likedByMe = uid != null && supabase.from("recipe_likes")
            .select {
                filter { eq("recipe_id", recipeId); eq("user_id", uid) }
                limit(1)
            }
            .decodeList<Map<String, String>>()
            .isNotEmpty()
        val savedByMe = uid != null && supabase.from("saved_recipes")
            .select {
                filter { eq("recipe_id", recipeId); eq("user_id", uid) }
                limit(1)
            }
            .decodeList<Map<String, String>>()
            .isNotEmpty()

        AppResult.Success(RecipeDetail(recipe, ingredients, steps, author, likedByMe, savedByMe))
    } catch (e: Exception) {
        AppResult.Failure(e.toAppError())
    }

    override suspend fun myRecipes(): AppResult<List<Recipe>> {
        val uid = auth.currentUserId ?: return AppResult.Failure(AppError.Unauthorized())
        return try {
            AppResult.Success(
                supabase.from("recipes")
                    .select {
                        filter { eq("author_id", uid) }
                        order("created_at", Order.DESCENDING)
                    }
                    .decodeList<Recipe>()
            )
        } catch (e: Exception) {
            AppResult.Failure(e.toAppError())
        }
    }

    override suspend fun savedRecipes(): AppResult<List<Recipe>> {
        val uid = auth.currentUserId ?: return AppResult.Failure(AppError.Unauthorized())
        return try {
            val savedIds = supabase.from("saved_recipes")
                .select(Columns.list("recipe_id")) { filter { eq("user_id", uid) } }
                .decodeList<Map<String, String>>()
                .mapNotNull { it["recipe_id"] }
            if (savedIds.isEmpty()) return AppResult.Success(emptyList())
            AppResult.Success(
                supabase.from("recipes")
                    .select {
                        filter {
                            isIn("id", savedIds)
                            eq("status", "published")
                        }
                    }
                    .decodeList<Recipe>()
            )
        } catch (e: Exception) {
            AppResult.Failure(e.toAppError())
        }
    }

    override suspend fun publish(draft: RecipeDraft): AppResult<Recipe> {
        val uid = auth.currentUserId ?: return AppResult.Failure(AppError.Unauthorized())
        val validation = Validators.validateRecipeForPublish(
            draft.recipe, draft.ingredients, draft.steps,
            hasCoverImage = draft.coverImageBytes != null || draft.recipe.coverImageUrl != null,
        )
        if (!validation.isValid) {
            return AppResult.Failure(AppError.Validation(validation.errors.first()))
        }
        return try {
            // Upload new cover image first if provided.
            val coverUrl = if (draft.coverImageBytes != null) {
                when (val upload = storage.uploadRecipeImage(draft.coverImageBytes)) {
                    is AppResult.Success -> upload.data
                    is AppResult.Failure -> return upload
                }
            } else draft.recipe.coverImageUrl

            val payload = RecipeUpsertDto(
                authorId = uid,
                title = draft.recipe.title.trim(),
                description = draft.recipe.description.trim(),
                coverImageUrl = coverUrl,
                prepMinutes = draft.recipe.prepMinutes,
                cookMinutes = draft.recipe.cookMinutes,
                totalMinutes = draft.recipe.totalMinutes,
                difficulty = draft.recipe.difficulty.name.lowercase(),
                servings = draft.recipe.servings,
                cuisine = draft.recipe.cuisine,
                tags = draft.recipe.tags,
                dietTags = draft.recipe.dietTags,
                youtubeUrl = draft.recipe.youtubeUrl?.trim()?.ifBlank { null },
                tips = draft.recipe.tips,
                status = "published",
            )

            val saved: Recipe = if (draft.recipe.id.isBlank()) {
                supabase.from("recipes").insert(payload) { select(Columns.ALL) }.decodeSingle()
            } else {
                supabase.from("recipes").update(payload) {
                    filter { eq("id", draft.recipe.id); eq("author_id", uid) }
                    select(Columns.ALL)
                }.decodeSingle()
            }

            // Replace relations wholesale (simple + correct for this scale).
            supabase.from("recipe_ingredients").delete { filter { eq("recipe_id", saved.id) } }
            supabase.from("recipe_steps").delete { filter { eq("recipe_id", saved.id) } }
            if (draft.ingredients.isNotEmpty()) {
                supabase.from("recipe_ingredients").insert(
                    draft.ingredients.mapIndexed { i, ing ->
                        mapOf(
                            "recipe_id" to saved.id,
                            "name" to ing.name.trim(),
                            "quantity" to ing.quantity,
                            "unit" to ing.unit,
                            "sort_order" to i.toString(),
                            "substitution" to ing.substitution,
                        )
                    }
                )
            }
            if (draft.steps.isNotEmpty()) {
                supabase.from("recipe_steps").insert(
                    draft.steps.mapIndexed { i, step ->
                        mapOf(
                            "recipe_id" to saved.id,
                            "step_number" to (i + 1).toString(),
                            "instruction" to step.instruction.trim(),
                        )
                    }
                )
            }
            AppResult.Success(saved)
        } catch (e: Exception) {
            AppResult.Failure(e.toAppError())
        }
    }

    override suspend fun unpublish(recipeId: String): AppResult<Unit> =
        setStatus(recipeId, RecipeStatus.DRAFT)

    private suspend fun setStatus(recipeId: String, status: RecipeStatus): AppResult<Unit> {
        val uid = auth.currentUserId ?: return AppResult.Failure(AppError.Unauthorized())
        return try {
            supabase.from("recipes").update(
                { set("status", status.name.lowercase()) }
            ) {
                filter { eq("id", recipeId); eq("author_id", uid) }
            }
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Failure(e.toAppError())
        }
    }

    override suspend fun delete(recipeId: String): AppResult<Unit> {
        val uid = auth.currentUserId ?: return AppResult.Failure(AppError.Unauthorized())
        return try {
            supabase.from("recipes").delete {
                filter { eq("id", recipeId); eq("author_id", uid) }
            }
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Failure(e.toAppError())
        }
    }

    override suspend fun setLiked(recipeId: String, liked: Boolean): AppResult<Boolean> {
        val uid = auth.currentUserId ?: return AppResult.Failure(AppError.Unauthorized())
        return try {
            if (liked) {
                // Upsert prevents duplicate-like errors on double taps.
                supabase.from("recipe_likes").upsert(
                    mapOf("recipe_id" to recipeId, "user_id" to uid),
                )
            } else {
                supabase.from("recipe_likes").delete {
                    filter { eq("recipe_id", recipeId); eq("user_id", uid) }
                }
            }
            AppResult.Success(liked)
        } catch (e: Exception) {
            AppResult.Failure(e.toAppError())
        }
    }

    override suspend fun setSaved(recipeId: String, saved: Boolean): AppResult<Boolean> {
        val uid = auth.currentUserId ?: return AppResult.Failure(AppError.Unauthorized())
        return try {
            if (saved) {
                supabase.from("saved_recipes").upsert(
                    mapOf("recipe_id" to recipeId, "user_id" to uid),
                )
            } else {
                supabase.from("saved_recipes").delete {
                    filter { eq("recipe_id", recipeId); eq("user_id", uid) }
                }
            }
            AppResult.Success(saved)
        } catch (e: Exception) {
            AppResult.Failure(e.toAppError())
        }
    }

    override suspend fun search(query: String): AppResult<List<Recipe>> = try {
        val q = query.trim()
        val list = supabase.from("recipes")
            .select {
                filter {
                    eq("status", "published")
                    or {
                        ilike("title", "%$q%")
                        ilike("description", "%$q%")
                    }
                }
                order("like_count", Order.DESCENDING)
                limit(50)
            }
            .decodeList<Recipe>()
        AppResult.Success(list)
    } catch (e: Exception) {
        AppResult.Failure(e.toAppError())
    }

    override suspend fun searchByIntent(intent: RecipeSearchIntent): AppResult<List<Recipe>> = try {
        // Broad server-side filter, precise client-side ranking.
        val list = supabase.from("recipes")
            .select {
                filter {
                    eq("status", "published")
                    intent.maxTotalMinutes?.let { lte("total_minutes", it) }
                    if (intent.dietTags.isNotEmpty()) contains("diet_tags", intent.dietTags)
                }
                limit(100)
            }
            .decodeList<Recipe>()
        AppResult.Success(list.filter { RecipeFiltering.matchesIntent(emptyList(), it, intent) })
    } catch (e: Exception) {
        AppResult.Failure(e.toAppError())
    }
}
