package com.abbeysbite.app.ai

import com.abbeysbite.app.core.config.AppConfig
import com.abbeysbite.app.core.util.AppError
import com.abbeysbite.app.core.util.AppResult
import com.abbeysbite.app.core.util.toAppError
import com.abbeysbite.app.data.model.MealAnalysis
import com.abbeysbite.app.data.model.MealCorrection
import com.abbeysbite.app.data.model.MealEntry
import com.abbeysbite.app.data.model.PantryItem
import com.abbeysbite.app.data.model.UserPreferences
import com.abbeysbite.app.data.model.WeeklyInsight
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class GatewayRequest(
    val task: String,
    val system: String,
    val user: String,
    @SerialName("image_base64") val imageBase64: String? = null,
    val history: List<ChatMessage> = emptyList(),
)

@Serializable
private data class GatewayResponse(
    val text: String = "",
    val error: String? = null,
)

/**
 * Production [AiProvider]: Gemma (multimodal) served behind the `ai-gateway`
 * Supabase Edge Function. The function holds the model API key server-side —
 * the app only ever authenticates with the user's Supabase session.
 */
class GemmaGatewayProvider(
    private val supabase: SupabaseClient,
) : AiProvider {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun analyzeMeal(
        imageBytes: ByteArray?,
        description: String?,
        preferences: UserPreferences?,
        pantry: List<PantryItem>,
        correction: MealCorrection?,
        previousAnalysis: MealAnalysis?,
    ): AppResult<MealAnalysis> {
        if (imageBytes == null && description.isNullOrBlank()) {
            return AppResult.Failure(AppError.Validation("Add a photo or tell us what you’re eating."))
        }
        val request = GatewayRequest(
            task = "analyze",
            system = AiPrompts.mealAnalysisSystem(),
            user = AiPrompts.mealAnalysisUser(description, preferences, pantry, correction, previousAnalysis),
            imageBase64 = imageBytes?.let { Base64.encode(it) },
        )
        return invokeGateway(request, AppConfig.Ai.ANALYSIS_TIMEOUT_MS) { text ->
            AiResponseParser.parseMealAnalysis(text)
        }
    }

    override suspend fun chat(context: MealChatContext, userMessage: String): AppResult<String> {
        val request = GatewayRequest(
            task = "chat",
            system = AiPrompts.chatSystem(context),
            user = userMessage,
            history = context.history.takeLast(20),
        )
        return invokeGateway(request, AppConfig.Ai.CHAT_TIMEOUT_MS) { text ->
            if (text.isBlank()) AppResult.Failure(AppError.AiInvalidResponse())
            else AppResult.Success(text.trim())
        }
    }

    override suspend fun parseRecipeQuery(query: String): AppResult<RecipeSearchIntent> {
        val request = GatewayRequest(
            task = "recipe_query",
            system = AiPrompts.recipeQuerySystem(),
            user = query,
        )
        return invokeGateway(request, AppConfig.Ai.CHAT_TIMEOUT_MS) { text ->
            AiResponseParser.decode<RecipeSearchIntent>(text)
        }
    }

    override suspend fun weeklyInsight(
        entries: List<MealEntry>,
        preferences: UserPreferences?,
    ): AppResult<WeeklyInsight> {
        val request = GatewayRequest(
            task = "weekly_insight",
            system = AiPrompts.weeklyInsightSystem(),
            user = AiPrompts.weeklyInsightUser(entries, preferences),
        )
        return invokeGateway(request, AppConfig.Ai.ANALYSIS_TIMEOUT_MS) { text ->
            AiResponseParser.parseWeeklyInsight(text)
        }
    }

    private suspend fun <T> invokeGateway(
        request: GatewayRequest,
        timeoutMs: Long,
        transform: (String) -> AppResult<T>,
    ): AppResult<T> = try {
        withTimeout(timeoutMs) {
            val response = supabase.functions.invoke(
                function = "ai-gateway",
                body = request,
            )
            when {
                response.status == HttpStatusCode.Unauthorized ->
                    AppResult.Failure(AppError.Unauthorized())
                response.status == HttpStatusCode.TooManyRequests ->
                    AppResult.Failure(AppError.LimitReached("You’ve reached today’s analysis limit."))
                !response.status.isSuccessValue() ->
                    AppResult.Failure(AppError.AiUnavailable())
                else -> {
                    val payload: GatewayResponse = json.decodeFromString(response.body<String>())
                    if (payload.error != null) {
                        AppResult.Failure(AppError.AiUnavailable())
                    } else {
                        transform(payload.text)
                    }
                }
            }
        }
    } catch (e: CancellationException) {
        if (e::class.simpleName == "TimeoutCancellationException") {
            AppResult.Failure(AppError.Timeout(e))
        } else throw e
    } catch (e: Throwable) {
        AppResult.Failure(e.toAppError())
    }

    private fun HttpStatusCode.isSuccessValue(): Boolean = value in 200..299
}
