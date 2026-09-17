package com.abbeysbite.app.features.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abbeysbite.app.analytics.Analytics
import com.abbeysbite.app.analytics.AnalyticsEvents
import com.abbeysbite.app.data.model.CookingSetup
import com.abbeysbite.app.data.model.DietPreference
import com.abbeysbite.app.data.model.EatingGoal
import com.abbeysbite.app.data.repository.PantryRepository
import com.abbeysbite.app.data.repository.PreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val step: Int = 0,
    val goals: Set<EatingGoal> = emptySet(),
    val dietPreference: DietPreference? = null,
    val avoidFoods: List<String> = emptyList(),
    val avoidInput: String = "",
    val cookingSetup: CookingSetup? = null,
    val pantryItems: List<String> = emptyList(),
    val pantryInput: String = "",
    val saving: Boolean = false,
    /** Non-null when saving to the backend failed — never silently dropped. */
    val saveError: String? = null,
) {
    companion object {
        const val STEP_COUNT = 5
    }
}

class OnboardingViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val pantryRepository: PantryRepository,
    private val analytics: Analytics,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun toggleGoal(goal: EatingGoal) = _state.update {
        it.copy(goals = if (goal in it.goals) it.goals - goal else it.goals + goal)
    }

    fun selectDiet(diet: DietPreference) = _state.update { it.copy(dietPreference = diet) }

    fun onAvoidInputChange(value: String) = _state.update { it.copy(avoidInput = value) }

    fun addAvoidFood() = _state.update {
        val food = it.avoidInput.trim()
        if (food.isEmpty() || it.avoidFoods.any { f -> f.equals(food, true) }) it.copy(avoidInput = "")
        else it.copy(avoidFoods = it.avoidFoods + food, avoidInput = "")
    }

    fun removeAvoidFood(food: String) = _state.update {
        it.copy(avoidFoods = it.avoidFoods - food)
    }

    fun selectCookingSetup(setup: CookingSetup) = _state.update { it.copy(cookingSetup = setup) }

    fun onPantryInputChange(value: String) = _state.update { it.copy(pantryInput = value) }

    fun addPantryItem() = _state.update {
        val item = it.pantryInput.trim()
        if (item.isEmpty() || it.pantryItems.any { p -> p.equals(item, true) }) it.copy(pantryInput = "")
        else it.copy(pantryItems = it.pantryItems + item, pantryInput = "")
    }

    fun removePantryItem(item: String) = _state.update {
        it.copy(pantryItems = it.pantryItems - item)
    }

    fun next() = _state.update { it.copy(step = (it.step + 1).coerceAtMost(OnboardingUiState.STEP_COUNT - 1)) }
    fun back() = _state.update { it.copy(step = (it.step - 1).coerceAtLeast(0)) }

    /**
     * Persists whatever was answered (all steps are skippable). A failed
     * backend write is surfaced and blocks completion — answers are never
     * silently discarded.
     */
    fun finish(onDone: () -> Unit) {
        val s = _state.value
        _state.update { it.copy(saving = true, saveError = null) }
        viewModelScope.launch {
            val existing = preferencesRepository.load().getOrNull()
            val saveResult = preferencesRepository.save(
                (existing ?: com.abbeysbite.app.data.model.UserPreferences()).copy(
                    goals = s.goals.toList(),
                    dietPreference = s.dietPreference ?: DietPreference.NO_PREFERENCE,
                    avoidFoods = s.avoidFoods,
                    cookingSetup = s.cookingSetup ?: CookingSetup.FULL_KITCHEN,
                    onboardingCompleted = true,
                )
            )
            saveResult
                .onSuccess {
                    s.pantryItems.forEach { pantryRepository.add(it) }
                    analytics.track(AnalyticsEvents.ONBOARDING_COMPLETED)
                    _state.update { it.copy(saving = false) }
                    onDone()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            saving = false,
                            saveError = "Couldn’t save your answers (${error.message}) — check your connection and try again.",
                        )
                    }
                }
        }
    }
}
