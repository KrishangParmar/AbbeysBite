package com.abbeysbite.app.features.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abbeysbite.app.ai.AiProvider
import com.abbeysbite.app.analytics.Analytics
import com.abbeysbite.app.core.util.AppError
import com.abbeysbite.app.data.model.FeedSection
import com.abbeysbite.app.data.model.Recipe
import com.abbeysbite.app.data.repository.RecipeRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CommunityUiState(
    val section: FeedSection = FeedSection.FOR_YOU,
    val loading: Boolean = true,
    val recipes: List<Recipe> = emptyList(),
    val error: AppError? = null,
    /** In-memory feed cache per section for instant tab switches. */
    val cache: Map<FeedSection, List<Recipe>> = emptyMap(),
)

class CommunityViewModel(
    private val recipeRepository: RecipeRepository,
    private val analytics: Analytics,
) : ViewModel() {

    private val _state = MutableStateFlow(CommunityUiState())
    val state: StateFlow<CommunityUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        selectSection(FeedSection.FOR_YOU, force = true)
    }

    fun selectSection(section: FeedSection, force: Boolean = false) {
        val cached = _state.value.cache[section]
        _state.update {
            it.copy(
                section = section,
                recipes = cached ?: it.recipes,
                loading = cached == null || force,
                error = null,
            )
        }
        if (cached != null && !force) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            recipeRepository.feed(section)
                .onSuccess { recipes ->
                    _state.update {
                        it.copy(
                            loading = false,
                            recipes = if (it.section == section) recipes else it.recipes,
                            cache = it.cache + (section to recipes),
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(loading = false, error = error) }
                }
        }
    }

    fun refresh() = selectSection(_state.value.section, force = true)

    fun toggleLike(recipe: Recipe, liked: Boolean) {
        viewModelScope.launch {
            recipeRepository.setLiked(recipe.id, liked)
            refreshCachesAfterMutation()
        }
    }

    private suspend fun refreshCachesAfterMutation() {
        delay(150)
        val section = _state.value.section
        recipeRepository.feed(section).onSuccess { recipes ->
            _state.update {
                it.copy(recipes = recipes, cache = it.cache + (section to recipes))
            }
        }
    }
}
