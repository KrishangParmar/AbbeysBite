package com.abbeysbite.app.ai

import com.abbeysbite.app.core.util.AppError
import com.abbeysbite.app.core.util.AppResult
import com.abbeysbite.app.data.model.MealAnalysis
import com.abbeysbite.app.data.model.MealCorrection
import com.abbeysbite.app.data.model.MealEntry
import com.abbeysbite.app.data.model.PantryItem
import com.abbeysbite.app.data.model.UserPreferences
import com.abbeysbite.app.data.model.WeeklyInsight
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Thrown inside [AiProvider.streamChat] flows to carry a typed [AppError]. */
class AiStreamException(val error: AppError) : Exception(error.message)

@Serializable
enum class ChatRole {
    @SerialName("user") USER,
    @SerialName("assistant") ASSISTANT,
}

@Serializable
data class ChatMessage(
    val role: ChatRole,
    val content: String,
    @SerialName("sent_at") val sentAt: String = "",
)

/** Everything the model should know when answering a contextual chat turn. */
data class MealChatContext(
    val analysis: MealAnalysis?,
    val preferences: UserPreferences?,
    val pantry: List<PantryItem>,
    val history: List<ChatMessage>,
)

/** Search intent extracted from a natural-language recipe query. */
@Serializable
data class RecipeSearchIntent(
    /** Keywords to match against title/description/tags. */
    val keywords: List<String> = emptyList(),
    @SerialName("max_total_minutes") val maxTotalMinutes: Int? = null,
    @SerialName("diet_tags") val dietTags: List<String> = emptyList(),
    @SerialName("must_use_ingredients") val mustUseIngredients: List<String> = emptyList(),
    val budget: Boolean = false,
)

/**
 * Provider-agnostic AI abstraction. The app never talks to a specific model
 * directly — implementations may run on-device or behind a secure gateway,
 * and can be swapped without touching product code.
 *
 * Preferred production implementation: Gemma (multimodal) behind the
 * `ai-gateway` Supabase Edge Function, which holds the API key server-side.
 */
interface AiProvider {

    /**
     * Analyze a meal photo (and/or a text description) and return the
     * structured [MealAnalysis]. [imageBytes] is compressed JPEG data; either
     * the image or [description] must be provided.
     */
    suspend fun analyzeMeal(
        imageBytes: ByteArray?,
        description: String?,
        preferences: UserPreferences?,
        pantry: List<PantryItem>,
        correction: MealCorrection? = null,
        previousAnalysis: MealAnalysis? = null,
    ): AppResult<MealAnalysis>

    /** Contextual, pantry/preference-aware chat about the current meal. */
    suspend fun chat(
        context: MealChatContext,
        userMessage: String,
    ): AppResult<String>

    /**
     * Streaming variant of [chat]: emits incremental text chunks. Providers
     * without true streaming fall back to emitting the complete reply once.
     * Failures surface as [AiStreamException]. Cancelling collection cancels
     * generation.
     */
    fun streamChat(
        context: MealChatContext,
        userMessage: String,
    ): Flow<String> = flow {
        when (val result = chat(context, userMessage)) {
            is AppResult.Success -> emit(result.data)
            is AppResult.Failure -> throw AiStreamException(result.error)
        }
    }

    /** Translate a natural-language recipe query into structured search intent. */
    suspend fun parseRecipeQuery(query: String): AppResult<RecipeSearchIntent>

    /** Summarize a week of logged meals into gentle observations and ideas. */
    suspend fun weeklyInsight(
        entries: List<MealEntry>,
        preferences: UserPreferences?,
    ): AppResult<WeeklyInsight>
}
