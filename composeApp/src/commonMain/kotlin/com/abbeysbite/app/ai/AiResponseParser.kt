package com.abbeysbite.app.ai

import com.abbeysbite.app.core.util.AppError
import com.abbeysbite.app.core.util.AppResult
import com.abbeysbite.app.data.model.MealAnalysis
import com.abbeysbite.app.data.model.WeeklyInsight
import kotlinx.serialization.json.Json

/**
 * Defensive parsing of model output. Models occasionally wrap JSON in fences
 * or add prose — we extract the first JSON object and decode leniently.
 */
object AiResponseParser {

    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }

    /** Extracts the first balanced top-level JSON object from arbitrary text. */
    fun extractJsonObject(raw: String): String? {
        val start = raw.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until raw.length) {
            val c = raw[i]
            when {
                escaped -> escaped = false
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
                !inString && c == '{' -> depth++
                !inString && c == '}' -> {
                    depth--
                    if (depth == 0) return raw.substring(start, i + 1)
                }
            }
        }
        return null
    }

    inline fun <reified T> decode(raw: String): AppResult<T> {
        val payload = extractJsonObject(raw)
            ?: return AppResult.Failure(AppError.AiInvalidResponse())
        return try {
            AppResult.Success(json.decodeFromString<T>(payload))
        } catch (e: Exception) {
            AppResult.Failure(AppError.AiInvalidResponse(e))
        }
    }

    fun parseMealAnalysis(raw: String): AppResult<MealAnalysis> {
        val result = decode<MealAnalysis>(raw)
        val analysis = result.getOrNull() ?: return result
        // Sanity: a usable analysis needs at least a name or components.
        if (analysis.detectedMealName.isBlank() && analysis.components.isEmpty()) {
            return AppResult.Failure(AppError.AiInvalidResponse())
        }
        return AppResult.Success(analysis.copy(suggestedAdditions = analysis.suggestedAdditions.take(4)))
    }

    fun parseWeeklyInsight(raw: String): AppResult<WeeklyInsight> {
        val result = decode<WeeklyInsight>(raw)
        val insight = result.getOrNull() ?: return result
        return AppResult.Success(
            insight.copy(
                observations = insight.observations.take(3),
                ideas = insight.ideas.take(2),
            )
        )
    }
}
