package com.abbeysbite.app.ai

import com.abbeysbite.app.billing.DebugDevSettings
import com.abbeysbite.app.core.util.AppResult
import com.abbeysbite.app.data.model.MealAnalysis
import com.abbeysbite.app.data.model.MealCorrection
import com.abbeysbite.app.data.model.MealEntry
import com.abbeysbite.app.data.model.PantryItem
import com.abbeysbite.app.data.model.UserPreferences
import com.abbeysbite.app.data.model.WeeklyInsight
import kotlinx.coroutines.flow.Flow

/**
 * DEBUG builds only: per-call switch between the real on-device provider and
 * the deterministic mock (You → Developer → "Use mock AI"). Release builds
 * bind [LocalGemmaProvider] directly — this class never ships active.
 */
class DebugSwitchingAiProvider(
    private val real: AiProvider,
    private val mock: AiProvider,
    private val devSettings: DebugDevSettings,
) : AiProvider {

    private fun active(): AiProvider =
        if (devSettings.useMockAi.value) mock else real

    override suspend fun analyzeMeal(
        imageBytes: ByteArray?,
        description: String?,
        preferences: UserPreferences?,
        pantry: List<PantryItem>,
        correction: MealCorrection?,
        previousAnalysis: MealAnalysis?,
    ): AppResult<MealAnalysis> =
        active().analyzeMeal(imageBytes, description, preferences, pantry, correction, previousAnalysis)

    override suspend fun chat(context: MealChatContext, userMessage: String): AppResult<String> =
        active().chat(context, userMessage)

    override fun streamChat(context: MealChatContext, userMessage: String): Flow<String> =
        active().streamChat(context, userMessage)

    override suspend fun parseRecipeQuery(query: String): AppResult<RecipeSearchIntent> =
        active().parseRecipeQuery(query)

    override suspend fun weeklyInsight(
        entries: List<MealEntry>,
        preferences: UserPreferences?,
    ): AppResult<WeeklyInsight> = active().weeklyInsight(entries, preferences)
}
