package com.abbeysbite.app.features.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abbeysbite.app.analytics.Analytics
import com.abbeysbite.app.analytics.AnalyticsEvents
import com.abbeysbite.app.core.util.AppError
import com.abbeysbite.app.data.model.RecipeDetail
import com.abbeysbite.app.data.model.ReportTargetType
import com.abbeysbite.app.data.repository.AuthRepository
import com.abbeysbite.app.data.repository.FriendsRepository
import com.abbeysbite.app.data.repository.RecipeRepository
import com.abbeysbite.app.data.repository.ReportsRepository
import com.abbeysbite.app.platform.HapticsService
import com.abbeysbite.app.platform.ShareService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecipeDetailUiState(
    val loading: Boolean = true,
    val detail: RecipeDetail? = null,
    val error: AppError? = null,
    val liked: Boolean = false,
    val saved: Boolean = false,
    val likeCount: Int = 0,
    val isOwner: Boolean = false,
    val reportSheetOpen: Boolean = false,
    val reportSubmitted: Boolean = false,
    val ownerActionDone: Boolean = false,
)

class RecipeDetailViewModel(
    private val recipeRepository: RecipeRepository,
    private val reportsRepository: ReportsRepository,
    private val friendsRepository: FriendsRepository,
    private val authRepository: AuthRepository,
    private val shareService: ShareService,
    private val haptics: HapticsService,
    private val analytics: Analytics,
    private val notificationSender: com.abbeysbite.app.core.session.NotificationSender,
) : ViewModel() {

    private val _state = MutableStateFlow(RecipeDetailUiState())
    val state: StateFlow<RecipeDetailUiState> = _state.asStateFlow()

    private var recipeId: String? = null

    fun load(id: String) {
        recipeId = id
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            recipeRepository.detail(id)
                .onSuccess { detail ->
                    analytics.track(AnalyticsEvents.RECIPE_VIEWED)
                    _state.update {
                        it.copy(
                            loading = false,
                            detail = detail,
                            liked = detail.likedByMe,
                            saved = detail.savedByMe,
                            likeCount = detail.recipe.likeCount,
                            isOwner = detail.recipe.authorId == authRepository.currentUserId,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(loading = false, error = error) }
                }
        }
    }

    fun toggleLike() {
        val id = recipeId ?: return
        val newValue = !_state.value.liked
        haptics.lightTap()
        // Optimistic update; revert on failure.
        _state.update {
            it.copy(liked = newValue, likeCount = (it.likeCount + if (newValue) 1 else -1).coerceAtLeast(0))
        }
        viewModelScope.launch {
            recipeRepository.setLiked(id, newValue)
                .onSuccess {
                    val detail = _state.value.detail
                    if (newValue && detail != null && !_state.value.isOwner) {
                        notificationSender.recipeLiked(
                            detail.recipe.authorId, detail.recipe.id,
                        )
                    }
                }
                .onFailure {
                    _state.update {
                        it.copy(liked = !newValue, likeCount = (it.likeCount + if (newValue) -1 else 1).coerceAtLeast(0))
                    }
                }
        }
    }

    fun toggleSave() {
        val id = recipeId ?: return
        val newValue = !_state.value.saved
        haptics.lightTap()
        _state.update { it.copy(saved = newValue) }
        viewModelScope.launch {
            recipeRepository.setSaved(id, newValue)
                .onSuccess { if (newValue) analytics.track(AnalyticsEvents.RECIPE_SAVED) }
                .onFailure { _state.update { it.copy(saved = !newValue) } }
        }
    }

    fun share() {
        val detail = _state.value.detail ?: return
        val recipe = detail.recipe
        shareService.shareText(
            "${recipe.title} — a hidden gem on ${com.abbeysbite.app.core.config.Brand.appName}. " +
                "${recipe.totalMinutes} minutes, ${detail.ingredients.size} ingredients."
        )
    }

    fun openReportSheet() = _state.update { it.copy(reportSheetOpen = true) }
    fun closeReportSheet() = _state.update { it.copy(reportSheetOpen = false) }

    fun reportRecipe(reason: String, details: String?) {
        val id = recipeId ?: return
        viewModelScope.launch {
            reportsRepository.submitReport(ReportTargetType.RECIPE, id, reason, details)
            _state.update { it.copy(reportSheetOpen = false, reportSubmitted = true) }
        }
    }

    fun blockAuthor() {
        val authorId = _state.value.detail?.recipe?.authorId ?: return
        viewModelScope.launch {
            friendsRepository.block(authorId)
            _state.update { it.copy(reportSheetOpen = false, reportSubmitted = true) }
        }
    }

    fun unpublish() {
        val id = recipeId ?: return
        viewModelScope.launch {
            recipeRepository.unpublish(id).onSuccess {
                _state.update { it.copy(ownerActionDone = true) }
            }
        }
    }

    fun delete() {
        val id = recipeId ?: return
        viewModelScope.launch {
            recipeRepository.delete(id).onSuccess {
                _state.update { it.copy(ownerActionDone = true) }
            }
        }
    }
}
