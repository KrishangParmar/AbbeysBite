package com.abbeysbite.app.data.repository

import com.abbeysbite.app.core.util.AppError
import com.abbeysbite.app.core.util.AppResult
import com.abbeysbite.app.core.util.appRunCatching
import com.abbeysbite.app.core.util.toAppError
import com.abbeysbite.app.data.model.CookingSetup
import com.abbeysbite.app.data.model.DietPreference
import com.abbeysbite.app.data.model.EatingGoal
import com.abbeysbite.app.data.model.PantryItem
import com.abbeysbite.app.data.model.ProgressSharingPreferences
import com.abbeysbite.app.data.model.UserPreferences
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Upsert payload without server-managed columns (updated_at is trigger-set). */
@Serializable
private data class UserPreferencesUpsert(
    @SerialName("user_id") val userId: String,
    val goals: List<EatingGoal>,
    @SerialName("diet_preference") val dietPreference: DietPreference,
    @SerialName("avoid_foods") val avoidFoods: List<String>,
    @SerialName("cooking_setup") val cookingSetup: CookingSetup,
    @SerialName("onboarding_completed") val onboardingCompleted: Boolean,
)

@Serializable
private data class SharingPreferencesUpsert(
    @SerialName("user_id") val userId: String,
    @SerialName("share_active_days") val shareActiveDays: Boolean,
    @SerialName("share_meals_logged") val shareMealsLogged: Boolean,
    @SerialName("share_plant_variety") val sharePlantVariety: Boolean,
    @SerialName("share_recipes_published") val shareRecipesPublished: Boolean,
    @SerialName("share_recipe_achievements") val shareRecipeAchievements: Boolean,
)

class SupabasePreferencesRepository(
    private val supabase: SupabaseClient,
    private val auth: AuthRepository,
) : PreferencesRepository, UserScopedState {

    private val _preferences = MutableStateFlow<UserPreferences?>(null)
    override val preferences: StateFlow<UserPreferences?> = _preferences.asStateFlow()

    override fun clearUserState() {
        _preferences.value = null
    }

    private fun userId(): String? = auth.currentUserId

    override suspend fun load(): AppResult<UserPreferences> {
        val uid = userId() ?: return AppResult.Failure(AppError.Unauthorized())
        return try {
            val existing = supabase.from("user_preferences")
                .select {
                    filter { eq("user_id", uid) }
                    limit(1)
                }
                .decodeList<UserPreferences>()
                .firstOrNull()
            val prefs = existing ?: UserPreferences(userId = uid)
            _preferences.value = prefs
            AppResult.Success(prefs)
        } catch (e: Exception) {
            AppResult.Failure(e.toAppError())
        }
    }

    override suspend fun save(preferences: UserPreferences): AppResult<Unit> {
        val uid = userId() ?: return AppResult.Failure(AppError.Unauthorized())
        return try {
            supabase.from("user_preferences").upsert(
                UserPreferencesUpsert(
                    userId = uid,
                    goals = preferences.goals,
                    dietPreference = preferences.dietPreference,
                    avoidFoods = preferences.avoidFoods,
                    cookingSetup = preferences.cookingSetup,
                    onboardingCompleted = preferences.onboardingCompleted,
                )
            )
            _preferences.value = preferences.copy(userId = uid)
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Failure(e.toAppError())
        }
    }

    override suspend fun loadSharingPreferences(): AppResult<ProgressSharingPreferences> {
        val uid = userId() ?: return AppResult.Failure(AppError.Unauthorized())
        return try {
            val existing = supabase.from("progress_sharing_preferences")
                .select {
                    filter { eq("user_id", uid) }
                    limit(1)
                }
                .decodeList<ProgressSharingPreferences>()
                .firstOrNull()
            AppResult.Success(existing ?: ProgressSharingPreferences(userId = uid))
        } catch (e: Exception) {
            AppResult.Failure(e.toAppError())
        }
    }

    override suspend fun saveSharingPreferences(prefs: ProgressSharingPreferences): AppResult<Unit> {
        val uid = userId() ?: return AppResult.Failure(AppError.Unauthorized())
        return appRunCatching {
            supabase.from("progress_sharing_preferences").upsert(
                SharingPreferencesUpsert(
                    userId = uid,
                    shareActiveDays = prefs.shareActiveDays,
                    shareMealsLogged = prefs.shareMealsLogged,
                    sharePlantVariety = prefs.sharePlantVariety,
                    shareRecipesPublished = prefs.shareRecipesPublished,
                    shareRecipeAchievements = prefs.shareRecipeAchievements,
                )
            )
            Unit
        }
    }
}

class SupabasePantryRepository(
    private val supabase: SupabaseClient,
    private val auth: AuthRepository,
) : PantryRepository, UserScopedState {

    private val _items = MutableStateFlow<List<PantryItem>>(emptyList())
    override val items: StateFlow<List<PantryItem>> = _items.asStateFlow()

    override fun clearUserState() {
        _items.value = emptyList()
    }

    override suspend fun load(): AppResult<List<PantryItem>> {
        val uid = auth.currentUserId ?: return AppResult.Failure(AppError.Unauthorized())
        return try {
            val list = supabase.from("pantry_items")
                .select {
                    filter { eq("user_id", uid) }
                    order("name", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
                }
                .decodeList<PantryItem>()
            _items.value = list
            AppResult.Success(list)
        } catch (e: Exception) {
            AppResult.Failure(e.toAppError())
        }
    }

    override suspend fun add(name: String, category: String?): AppResult<PantryItem> {
        val uid = auth.currentUserId ?: return AppResult.Failure(AppError.Unauthorized())
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return AppResult.Failure(AppError.Validation("Item name can’t be empty."))
        if (_items.value.any { it.name.equals(trimmed, ignoreCase = true) }) {
            return AppResult.Failure(AppError.Validation("That’s already in your pantry."))
        }
        return try {
            val inserted = supabase.from("pantry_items")
                .insert(mapOf("user_id" to uid, "name" to trimmed, "category" to category)) {
                    select(Columns.ALL)
                }
                .decodeSingle<PantryItem>()
            _items.value = (_items.value + inserted).sortedBy { it.name.lowercase() }
            AppResult.Success(inserted)
        } catch (e: Exception) {
            AppResult.Failure(e.toAppError())
        }
    }

    override suspend fun remove(id: String): AppResult<Unit> = try {
        supabase.from("pantry_items").delete { filter { eq("id", id) } }
        _items.value = _items.value.filterNot { it.id == id }
        AppResult.Success(Unit)
    } catch (e: Exception) {
        AppResult.Failure(e.toAppError())
    }
}
