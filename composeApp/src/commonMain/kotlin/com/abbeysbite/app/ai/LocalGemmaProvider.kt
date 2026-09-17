package com.abbeysbite.app.ai

import com.abbeysbite.app.ai.local.EngineState
import com.abbeysbite.app.ai.local.LocalAiEngine
import com.abbeysbite.app.ai.local.LocalGenerationRequest
import com.abbeysbite.app.ai.local.ModelManager
import com.abbeysbite.app.ai.local.ModelState
import com.abbeysbite.app.core.util.AppError
import com.abbeysbite.app.core.util.AppResult
import com.abbeysbite.app.data.model.MealAnalysis
import com.abbeysbite.app.data.model.MealCorrection
import com.abbeysbite.app.data.model.MealEntry
import com.abbeysbite.app.data.model.PantryItem
import com.abbeysbite.app.data.model.UserPreferences
import com.abbeysbite.app.data.model.WeeklyInsight
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * PRIMARY production [AiProvider]: Gemma 3n running entirely on this device
 * through [LocalAiEngine]. Meal photos and conversations never leave the
 * phone. There is deliberately no hidden cloud fallback — when the local
 * engine can't run, the specific local failure is surfaced.
 */
class LocalGemmaProvider(
    private val engine: LocalAiEngine,
    private val modelManager: ModelManager,
) : AiProvider {

    private val prepareMutex = Mutex()

    /** Gemma chat-template rendering (no system role: merged into first turn). */
    private fun renderPrompt(
        system: String,
        history: List<ChatMessage> = emptyList(),
        user: String,
    ): String = buildString {
        // The window must open with a USER turn: the system text is merged
        // into the first user turn, and a window starting with an assistant
        // message would drop the system prompt AND malform the template.
        val trimmedHistory = history.takeLast(MAX_HISTORY_MESSAGES)
            .dropWhile { it.role == ChatRole.ASSISTANT }
        if (trimmedHistory.isEmpty()) {
            append("<start_of_turn>user\n")
            append(system.trim())
            append("\n\n")
            append(user.trim())
            append("<end_of_turn>\n")
        } else {
            trimmedHistory.forEachIndexed { index, message ->
                when (message.role) {
                    ChatRole.USER -> {
                        append("<start_of_turn>user\n")
                        if (index == 0) {
                            append(system.trim())
                            append("\n\n")
                        }
                        append(message.content.trim())
                        append("<end_of_turn>\n")
                    }
                    ChatRole.ASSISTANT -> {
                        append("<start_of_turn>model\n")
                        append(message.content.trim())
                        append("<end_of_turn>\n")
                    }
                }
            }
            append("<start_of_turn>user\n")
            append(user.trim())
            append("<end_of_turn>\n")
        }
        append("<start_of_turn>model\n")
    }

    /** Ensures the model file is installed and loaded; returns typed failure. */
    private suspend fun ensureReady(): AppError? {
        if (!engine.isSupported) {
            return AppError.ModelFailed(
                "On-device AI isn’t available on this platform build yet.",
            )
        }
        val modelState = when (val s = modelManager.state.value) {
            is ModelState.Installed -> s
            is ModelState.Checking -> {
                (modelManager.refreshNow() as? ModelState.Installed)
                    ?: return AppError.ModelNotReady()
            }
            else -> return AppError.ModelNotReady()
        }
        return prepareMutex.withLock {
            when (engine.state.value) {
                is EngineState.Ready -> null
                else -> runCatching { engine.prepareModel(modelState.path) }
                    .exceptionOrNull()
                    ?.let { e ->
                        if (e is CancellationException) throw e
                        AppError.ModelFailed(
                            "Your private AI couldn’t start: ${e.message ?: "unknown error"}",
                            e,
                        )
                    }
            }
        }
    }

    private suspend fun <T> generateAndParse(
        request: LocalGenerationRequest,
        parse: (String) -> AppResult<T>,
    ): AppResult<T> {
        ensureReady()?.let { return AppResult.Failure(it) }
        return try {
            val raw = engine.generate(request)
            parse(raw)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            AppResult.Failure(AppError.ModelFailed("Generation failed: ${e.message}", e))
        }
    }

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
        val prompt = renderPrompt(
            system = AiPrompts.mealAnalysisSystem(),
            user = AiPrompts.mealAnalysisUser(description, preferences, pantry, correction, previousAnalysis),
        )
        return generateAndParse(
            LocalGenerationRequest(
                prompt = prompt,
                imageJpeg = imageBytes,
                // The structured analysis fits in ~400 tokens; a tight budget
                // keeps on-device latency low (see PERFORMANCE.md).
                maxOutputTokens = 640,
                temperature = 0.35f,
            ),
            AiResponseParser::parseMealAnalysis,
        )
    }

    override suspend fun chat(context: MealChatContext, userMessage: String): AppResult<String> {
        val prompt = renderPrompt(
            system = AiPrompts.chatSystem(context),
            history = context.history,
            user = userMessage,
        )
        return generateAndParse(
            LocalGenerationRequest(prompt = prompt, maxOutputTokens = 320, temperature = 0.7f),
        ) { raw ->
            if (raw.isBlank()) AppResult.Failure(AppError.AiInvalidResponse())
            else AppResult.Success(raw.trim())
        }
    }

    override fun streamChat(context: MealChatContext, userMessage: String): Flow<String> = flow {
        ensureReady()?.let { throw AiStreamException(it) }
        val prompt = renderPrompt(
            system = AiPrompts.chatSystem(context),
            history = context.history,
            user = userMessage,
        )
        engine.streamGenerate(
            LocalGenerationRequest(prompt = prompt, maxOutputTokens = 320, temperature = 0.7f),
        ).collect { chunk -> emit(chunk) }
    }

    override suspend fun parseRecipeQuery(query: String): AppResult<RecipeSearchIntent> {
        val prompt = renderPrompt(system = AiPrompts.recipeQuerySystem(), user = query)
        return generateAndParse(
            LocalGenerationRequest(prompt = prompt, maxOutputTokens = 256, temperature = 0.2f),
        ) { raw ->
            AiResponseParser.decode<RecipeSearchIntent>(raw)
        }
    }

    override suspend fun weeklyInsight(
        entries: List<MealEntry>,
        preferences: UserPreferences?,
    ): AppResult<WeeklyInsight> {
        val prompt = renderPrompt(
            system = AiPrompts.weeklyInsightSystem(),
            user = AiPrompts.weeklyInsightUser(entries, preferences),
        )
        return generateAndParse(
            LocalGenerationRequest(prompt = prompt, maxOutputTokens = 512, temperature = 0.5f),
            AiResponseParser::parseWeeklyInsight,
        )
    }

    fun cancelGeneration() = engine.cancelGeneration()

    private companion object {
        const val MAX_HISTORY_MESSAGES = 12
    }
}
