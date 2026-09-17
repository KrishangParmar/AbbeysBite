package com.abbeysbite.app.features.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abbeysbite.app.analytics.Analytics
import com.abbeysbite.app.analytics.AnalyticsEvents
import com.abbeysbite.app.data.model.Recipe
import com.abbeysbite.app.data.model.RecipeDifficulty
import com.abbeysbite.app.data.model.RecipeIngredient
import com.abbeysbite.app.data.model.RecipeStep
import com.abbeysbite.app.data.repository.RecipeDraft
import com.abbeysbite.app.data.repository.RecipeRepository
import com.abbeysbite.app.domain.Validators
import com.abbeysbite.app.platform.MediaPicker
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class EditorIngredient(
    val name: String = "",
    val quantity: String = "",
    val unit: String = "",
    val substitution: String = "",
)

@Serializable
data class EditorDraftSnapshot(
    val title: String = "",
    val description: String = "",
    val prepMinutes: String = "",
    val cookMinutes: String = "",
    val servings: String = "2",
    val difficulty: RecipeDifficulty = RecipeDifficulty.EASY,
    val cuisine: String = "",
    val tags: List<String> = emptyList(),
    val dietTags: List<String> = emptyList(),
    val youtubeUrl: String = "",
    val tips: String = "",
    val ingredients: List<EditorIngredient> = listOf(EditorIngredient()),
    val steps: List<String> = listOf(""),
)

data class RecipeEditorUiState(
    val draft: EditorDraftSnapshot = EditorDraftSnapshot(),
    val coverImageBytes: ByteArray? = null,
    val existingCoverUrl: String? = null,
    val editingRecipeId: String? = null,
    val publishing: Boolean = false,
    val errors: List<String> = emptyList(),
    val published: Recipe? = null,
)

/**
 * Recipe creation/editing. Drafts autosave locally (per user) so nothing is
 * lost if the app closes before publishing.
 */
class RecipeEditorViewModel(
    private val recipeRepository: RecipeRepository,
    private val mediaPicker: MediaPicker,
    private val settings: Settings,
    private val analytics: Analytics,
) : ViewModel() {

    private companion object {
        const val DRAFT_KEY = "recipe_editor_draft"
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(RecipeEditorUiState())
    val state: StateFlow<RecipeEditorUiState> = _state.asStateFlow()

    /** Load a fresh draft (restoring autosave) or an existing recipe to edit. */
    fun initialize(recipeId: String?) {
        if (recipeId != null && _state.value.editingRecipeId != recipeId) {
            viewModelScope.launch {
                recipeRepository.detail(recipeId).onSuccess { detail ->
                    _state.update {
                        RecipeEditorUiState(
                            draft = EditorDraftSnapshot(
                                title = detail.recipe.title,
                                description = detail.recipe.description,
                                prepMinutes = detail.recipe.prepMinutes.toString(),
                                cookMinutes = detail.recipe.cookMinutes.toString(),
                                servings = detail.recipe.servings.toString(),
                                difficulty = detail.recipe.difficulty,
                                cuisine = detail.recipe.cuisine.orEmpty(),
                                tags = detail.recipe.tags,
                                dietTags = detail.recipe.dietTags,
                                youtubeUrl = detail.recipe.youtubeUrl.orEmpty(),
                                tips = detail.recipe.tips.orEmpty(),
                                ingredients = detail.ingredients.map { ing ->
                                    EditorIngredient(
                                        ing.name, ing.quantity.orEmpty(),
                                        ing.unit.orEmpty(), ing.substitution.orEmpty(),
                                    )
                                }.ifEmpty { listOf(EditorIngredient()) },
                                steps = detail.steps.map { it.instruction }.ifEmpty { listOf("") },
                            ),
                            existingCoverUrl = detail.recipe.coverImageUrl,
                            editingRecipeId = recipeId,
                        )
                    }
                }
            }
        } else if (recipeId == null && _state.value.editingRecipeId == null) {
            val stored = settings.getStringOrNull(DRAFT_KEY)?.let {
                runCatching { json.decodeFromString<EditorDraftSnapshot>(it) }.getOrNull()
            }
            if (stored != null) {
                _state.update { it.copy(draft = stored) }
            }
        }
    }

    fun update(transform: (EditorDraftSnapshot) -> EditorDraftSnapshot) {
        _state.update { it.copy(draft = transform(it.draft), errors = emptyList()) }
        // Autosave only NEW drafts; editing existing recipes shouldn't clobber it.
        if (_state.value.editingRecipeId == null) {
            settings.putString(DRAFT_KEY, json.encodeToString(_state.value.draft))
        }
    }

    fun pickCoverImage() {
        viewModelScope.launch {
            mediaPicker.pickPhoto()?.let { image ->
                _state.update { it.copy(coverImageBytes = image.bytes) }
            }
        }
    }

    fun captureCoverImage() {
        viewModelScope.launch {
            mediaPicker.capturePhoto()?.let { image ->
                _state.update { it.copy(coverImageBytes = image.bytes) }
            }
        }
    }

    fun publish() {
        val s = _state.value
        val d = s.draft
        val recipe = Recipe(
            id = s.editingRecipeId.orEmpty(),
            title = d.title.trim(),
            description = d.description.trim(),
            coverImageUrl = s.existingCoverUrl,
            prepMinutes = d.prepMinutes.toIntOrNull() ?: 0,
            cookMinutes = d.cookMinutes.toIntOrNull() ?: 0,
            difficulty = d.difficulty,
            servings = d.servings.toIntOrNull() ?: 0,
            cuisine = d.cuisine.trim().ifBlank { null },
            tags = d.tags,
            dietTags = d.dietTags,
            youtubeUrl = d.youtubeUrl.trim().ifBlank { null },
            tips = d.tips.trim().ifBlank { null },
        )
        val ingredients = d.ingredients
            .filter { it.name.isNotBlank() }
            .mapIndexed { i, ing ->
                RecipeIngredient(
                    name = ing.name.trim(),
                    quantity = ing.quantity.trim().ifBlank { null },
                    unit = ing.unit.trim().ifBlank { null },
                    substitution = ing.substitution.trim().ifBlank { null },
                    sortOrder = i,
                )
            }
        val steps = d.steps
            .filter { it.isNotBlank() }
            .mapIndexed { i, instruction ->
                RecipeStep(stepNumber = i + 1, instruction = instruction.trim())
            }

        val validation = Validators.validateRecipeForPublish(
            recipe, ingredients, steps,
            hasCoverImage = s.coverImageBytes != null || s.existingCoverUrl != null,
        )
        if (!validation.isValid) {
            _state.update { it.copy(errors = validation.errors) }
            return
        }

        _state.update { it.copy(publishing = true, errors = emptyList()) }
        viewModelScope.launch {
            recipeRepository.publish(
                RecipeDraft(recipe, ingredients, steps, s.coverImageBytes)
            )
                .onSuccess { published ->
                    analytics.track(AnalyticsEvents.RECIPE_PUBLISHED)
                    settings.remove(DRAFT_KEY)
                    _state.update { it.copy(publishing = false, published = published) }
                }
                .onFailure { error ->
                    _state.update { it.copy(publishing = false, errors = listOf(error.message)) }
                }
        }
    }
}
