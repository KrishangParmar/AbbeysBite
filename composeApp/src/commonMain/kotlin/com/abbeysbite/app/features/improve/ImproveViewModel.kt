package com.abbeysbite.app.features.improve

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abbeysbite.app.ai.AiProvider
import com.abbeysbite.app.analytics.Analytics
import com.abbeysbite.app.analytics.AnalyticsEvents
import com.abbeysbite.app.billing.BillingManager
import com.abbeysbite.app.billing.FreeTierLimiter
import com.abbeysbite.app.core.util.AppError
import com.abbeysbite.app.data.model.MealAnalysis
import com.abbeysbite.app.data.model.MealCorrection
import com.abbeysbite.app.data.model.MealEntry
import com.abbeysbite.app.data.model.MealType
import com.abbeysbite.app.data.repository.JournalRepository
import com.abbeysbite.app.data.repository.MediaStorageRepository
import com.abbeysbite.app.data.repository.PantryRepository
import com.abbeysbite.app.data.repository.PreferencesRepository
import com.abbeysbite.app.platform.HapticsService
import com.abbeysbite.app.platform.MediaPicker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Phases of the Improve flow. */
sealed class ImprovePhase {
    /** Initial input state: camera, library, or describe. */
    data object Input : ImprovePhase()

    /** Photo captured / text submitted; AI running with progressive status copy. */
    data class Analyzing(val statusText: String) : ImprovePhase()

    data class Result(val analysis: MealAnalysis) : ImprovePhase()

    data class Failed(val error: AppError) : ImprovePhase()

    /** Free-tier daily limit reached — invite to premium, gently. */
    data object LimitReached : ImprovePhase()
}

data class ImproveUiState(
    val phase: ImprovePhase = ImprovePhase.Input,
    val imageBytes: ByteArray? = null,
    val description: String = "",
    /** Non-null once the meal has been logged to the journal. */
    val loggedEntryId: String? = null,
    val logging: Boolean = false,
    val analysesRemaining: Int? = null,
    val correctionSheetOpen: Boolean = false,
    val logSheetOpen: Boolean = false,
    /** On-device model status driving the "private AI" setup surface. */
    val modelState: com.abbeysbite.app.ai.local.ModelState =
        com.abbeysbite.app.ai.local.ModelState.Checking,
    /** True when analyses can run right now (model ready or debug mock on). */
    val aiReady: Boolean = false,
)

class ImproveViewModel(
    private val aiProvider: AiProvider,
    private val session: ImproveSession,
    private val mediaPicker: MediaPicker,
    private val preferencesRepository: PreferencesRepository,
    private val pantryRepository: PantryRepository,
    private val journalRepository: JournalRepository,
    private val storageRepository: MediaStorageRepository,
    private val billingManager: BillingManager,
    private val limiter: FreeTierLimiter,
    private val haptics: HapticsService,
    private val analytics: Analytics,
    private val modelManager: com.abbeysbite.app.ai.local.ModelManager,
    private val localEngine: com.abbeysbite.app.ai.local.LocalAiEngine,
    private val devSettings: com.abbeysbite.app.billing.DebugDevSettings,
    private val platformInfo: com.abbeysbite.app.platform.PlatformInfo,
) : ViewModel() {

    private val _state = MutableStateFlow(ImproveUiState())
    val state: StateFlow<ImproveUiState> = _state.asStateFlow()

    /** Kept so contextual chat/voice can pick up the exact analysis context. */
    var lastAnalysis: MealAnalysis? = null
        private set

    init {
        refreshRemaining()
        viewModelScope.launch {
            preferencesRepository.load()
            pantryRepository.load()
        }
        // Track model readiness; warm the engine as soon as a model is
        // installed so the first analysis doesn't pay the cold load.
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                modelManager.state,
                devSettings.useMockAi,
            ) { model, mock -> model to mock }.collect { (model, mockOn) ->
                val mockActive = mockOn && platformInfo.isDebug
                _state.update {
                    it.copy(
                        modelState = model,
                        aiReady = mockActive ||
                            model is com.abbeysbite.app.ai.local.ModelState.Installed,
                    )
                }
                if (model is com.abbeysbite.app.ai.local.ModelState.Installed &&
                    !mockActive && localEngine.isSupported
                ) {
                    // Fire-and-forget warm-up; failures surface on first use.
                    runCatching { localEngine.prepareModel(model.path) }
                }
            }
        }
    }

    fun startModelDownload() = modelManager.startDownload()
    fun cancelModelDownload() = modelManager.cancelDownload()
    fun retryModelCheck() = modelManager.refresh()

    private fun today() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    private fun refreshRemaining() {
        val premium = billingManager.premiumState.value.isPremium
        _state.update { it.copy(analysesRemaining = limiter.analysesRemaining(premium, today())) }
    }

    fun onDescriptionChange(value: String) = _state.update { it.copy(description = value) }

    fun capturePhoto() = obtainImage { mediaPicker.capturePhoto() }

    fun pickPhoto() = obtainImage { mediaPicker.pickPhoto() }

    private fun obtainImage(source: suspend () -> com.abbeysbite.app.platform.PickedImage?) {
        viewModelScope.launch {
            val image = source() ?: return@launch // user cancelled
            haptics.lightTap()
            analyze(imageBytes = image.bytes, description = null)
        }
    }

    fun analyzeDescription() {
        val text = _state.value.description.trim()
        if (text.isEmpty()) return
        analyze(imageBytes = null, description = text)
    }

    private fun analyze(
        imageBytes: ByteArray?,
        description: String?,
        correction: MealCorrection? = null,
    ) {
        if (!_state.value.aiReady) {
            // Setup surface is already visible; never run against a missing model.
            return
        }
        val premium = billingManager.premiumState.value.isPremium
        if (correction == null && !limiter.canAnalyze(premium, today())) {
            _state.update { it.copy(phase = ImprovePhase.LimitReached) }
            return
        }
        val bytes = imageBytes ?: _state.value.imageBytes
        _state.update {
            it.copy(
                phase = ImprovePhase.Analyzing("Looking at your plate…"),
                imageBytes = bytes,
                loggedEntryId = null,
            )
        }
        analytics.track(AnalyticsEvents.MEAL_SCAN_STARTED)
        viewModelScope.launch {
            // Progressive status copy keeps the wait engaging.
            val statusJob = launch {
                val stages = listOf(
                    "Looking at your plate…" to 0L,
                    "Spotting ingredients…" to 2000L,
                    "Thinking about easy additions…" to 4500L,
                    "Almost there…" to 8000L,
                )
                for ((text, delayMs) in stages) {
                    kotlinx.coroutines.delay(delayMs)
                    _state.update { s ->
                        if (s.phase is ImprovePhase.Analyzing) s.copy(phase = ImprovePhase.Analyzing(text)) else s
                    }
                }
            }

            val prefs = preferencesRepository.preferences.value
            val premiumNow = billingManager.premiumState.value.isPremium
            val pantry = if (limiter.canUsePantryAwareAi(premiumNow)) {
                pantryRepository.items.value
            } else emptyList()

            val result = aiProvider.analyzeMeal(
                imageBytes = bytes,
                description = description ?: _state.value.description.trim().ifBlank { null },
                preferences = prefs,
                pantry = pantry,
                correction = correction,
                previousAnalysis = if (correction != null) lastAnalysis else null,
            )
            statusJob.cancel()

            result
                .onSuccess { analysis ->
                    if (correction == null) limiter.recordAnalysis(today())
                    refreshRemaining()
                    lastAnalysis = analysis
                    session.onNewAnalysis(analysis, bytes)
                    haptics.success()
                    analytics.track(
                        AnalyticsEvents.MEAL_SCAN_COMPLETED,
                        mapOf("has_photo" to (bytes != null).toString()),
                    )
                    _state.update { it.copy(phase = ImprovePhase.Result(analysis)) }
                }
                .onFailure { error ->
                    _state.update { it.copy(phase = ImprovePhase.Failed(error)) }
                }
        }
    }

    // ------------------------------------------------------------- correction

    fun openCorrectionSheet() = _state.update { it.copy(correctionSheetOpen = true) }
    fun closeCorrectionSheet() = _state.update { it.copy(correctionSheetOpen = false) }

    fun applyCorrection(correction: MealCorrection) {
        closeCorrectionSheet()
        analyze(imageBytes = _state.value.imageBytes, description = null, correction = correction)
    }

    // ------------------------------------------------------------- logging

    fun openLogSheet() = _state.update { it.copy(logSheetOpen = true) }
    fun closeLogSheet() = _state.update { it.copy(logSheetOpen = false) }

    fun logMeal(mealType: MealType) {
        val analysis = (state.value.phase as? ImprovePhase.Result)?.analysis ?: return
        _state.update { it.copy(logging = true, logSheetOpen = false) }
        viewModelScope.launch {
            val bytes = _state.value.imageBytes
            val photoPath = bytes?.let { storageRepository.uploadMealPhoto(it).getOrNull() }
            val now = Clock.System.now()
            val localDate = now.toLocalDateTime(TimeZone.currentSystemDefault()).date
            val result = journalRepository.add(
                MealEntry(
                    mealType = mealType,
                    mealName = analysis.detectedMealName,
                    photoPath = photoPath,
                    eatenAt = now.toString(),
                    entryDate = localDate.toString(),
                    analysis = analysis,
                )
            )
            result
                .onSuccess { entry ->
                    haptics.success()
                    analytics.track(AnalyticsEvents.MEAL_LOGGED, mapOf("type" to mealType.name.lowercase()))
                    _state.update { it.copy(logging = false, loggedEntryId = entry.id) }
                }
                .onFailure {
                    _state.update { it.copy(logging = false) }
                }
        }
    }

    fun suggestionOpened(name: String) {
        analytics.track(AnalyticsEvents.SUGGESTION_OPENED, mapOf("suggestion" to name))
    }

    fun reset() {
        lastAnalysis = null
        session.clear()
        _state.value = ImproveUiState()
        refreshRemaining()
    }

    fun retry() {
        val s = _state.value
        if (s.imageBytes != null || s.description.isNotBlank()) {
            analyze(s.imageBytes, s.description.trim().ifBlank { null })
        } else {
            reset()
        }
    }
}
