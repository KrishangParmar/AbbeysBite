package com.abbeysbite.app.ai

import com.abbeysbite.app.data.model.MealAnalysis
import com.abbeysbite.app.data.model.MealCorrection
import com.abbeysbite.app.data.model.MealEntry
import com.abbeysbite.app.data.model.PantryItem
import com.abbeysbite.app.data.model.UserPreferences

/**
 * Prompt construction for every AI task. Kept in one place so tone and
 * product philosophy ("never shame food, always help add") stay consistent
 * across providers.
 */
object AiPrompts {

    private const val PHILOSOPHY = """
You are a warm, practical kitchen assistant inside a nutrition app whose philosophy is
"Eat what you love. Add what helps."
Rules you must always follow:
- NEVER judge, shame or moralize about food. No words like bad, unhealthy, cheat, guilty, junk.
- Focus on practical ADDITIONS around protein, fibre and healthy fats.
- Suggestions must make culinary sense with the actual meal (flavor and texture compatible).
- Respect the user's diet preference, allergies/avoid list, cooking setup and pantry.
- Keep language friendly, concise and encouraging.
- Never give medical advice, diagnoses, calorie budgets or restrictive plans.
"""

    private fun contextBlock(preferences: UserPreferences?, pantry: List<PantryItem>): String =
        buildString {
            preferences?.let { p ->
                appendLine("User context:")
                appendLine("- Diet preference: ${p.dietPreference.label}")
                if (p.avoidFoods.isNotEmpty()) {
                    appendLine("- Avoids/allergies (NEVER suggest these): ${p.avoidFoods.joinToString()}")
                }
                appendLine("- Cooking setup: ${p.cookingSetup.label}")
            }
            if (pantry.isNotEmpty()) {
                appendLine("- Pantry (prefer these for suggestions, set pantry_match=true): ${pantry.joinToString { it.name }}")
            }
        }

    fun mealAnalysisSystem(): String = PHILOSOPHY + """
Analyze the meal in the image and/or description. Respond with ONLY a JSON object, no markdown fences, matching exactly:
{
  "detected_meal_name": string,
  "confidence": number 0..1,
  "components": [{"name": string, "confidence": number 0..1}],
  "protein_status": "present" | "could_add" | "uncertain",
  "protein_sources": [string],
  "fibre_status": "present" | "could_add" | "uncertain",
  "fibre_sources": [string],
  "healthy_fat_status": "present" | "could_add" | "uncertain",
  "healthy_fat_sources": [string],
  "suggested_additions": [
    {"name": string, "category": "protein"|"fibre"|"healthy_fat"|"general",
     "reason": string, "effort": "none"|"low"|"medium",
     "pantry_match": boolean, "optional_substitution": string|null}
  ],
  "notes": string
}
Provide 2-4 suggested_additions, ordered most-practical first. Reasons must reference this specific meal.
"""

    fun mealAnalysisUser(
        description: String?,
        preferences: UserPreferences?,
        pantry: List<PantryItem>,
        correction: MealCorrection?,
        previousAnalysis: MealAnalysis?,
    ): String = buildString {
        appendLine(contextBlock(preferences, pantry))
        if (!description.isNullOrBlank()) {
            appendLine("Meal description from user: $description")
        }
        if (correction != null && previousAnalysis != null) {
            appendLine("You analyzed this meal before as: ${previousAnalysis.detectedMealName}")
            appendLine("with components: ${previousAnalysis.components.joinToString { it.name }}")
            appendLine("The user corrected you — re-analyze honoring these corrections exactly:")
            correction.renamedMeal?.let { appendLine("- The meal is actually: $it") }
            if (correction.addedComponents.isNotEmpty()) {
                appendLine("- Also contains: ${correction.addedComponents.joinToString()}")
            }
            if (correction.removedComponents.isNotEmpty()) {
                appendLine("- Does NOT contain: ${correction.removedComponents.joinToString()}")
            }
        }
        if (description.isNullOrBlank() && correction == null) {
            appendLine("Analyze the attached meal photo.")
        }
    }

    fun chatSystem(context: MealChatContext): String = PHILOSOPHY + buildString {
        appendLine("You are chatting about the user's current meal. Keep replies SHORT and conversational —")
        appendLine("one to three actionable suggestions maximum, no essays, no bullet-point walls.")
        appendLine(contextBlock(context.preferences, context.pantry))
        context.analysis?.let { a ->
            appendLine("Current meal: ${a.detectedMealName}")
            appendLine("Components: ${a.components.joinToString { it.name }}")
            appendLine("Protein: ${a.proteinStatus} (${a.proteinSources.joinToString()})")
            appendLine("Fibre: ${a.fibreStatus} (${a.fibreSources.joinToString()})")
            appendLine("Healthy fat: ${a.healthyFatStatus} (${a.healthyFatSources.joinToString()})")
        }
    }

    fun recipeQuerySystem(): String = """
You convert a natural-language recipe request into structured search intent.
Respond with ONLY a JSON object, no markdown fences:
{
  "keywords": [string],
  "max_total_minutes": number|null,
  "diet_tags": [string],
  "must_use_ingredients": [string],
  "budget": boolean
}
diet_tags may include: vegetarian, vegan, gluten-free, dairy-free.
Extract time limits ("in 15 minutes" -> 15). "cheap"/"budget"/price mentions -> budget=true.
Ingredients the user says they have go into must_use_ingredients.
"""

    fun weeklyInsightSystem(): String = PHILOSOPHY + """
Review the user's week of logged meals. Respond with ONLY a JSON object:
{
  "observations": [string],  // max 3, gentle and specific, e.g. "Breakfast has consistently included protein this week."
  "ideas": [string]          // max 2, practical, e.g. "Keeping nuts or seeds nearby may make lunch easier."
}
Never mention calories, weight, deficits, or anything medical. Celebrate what's working before suggesting anything.
"""

    fun weeklyInsightUser(entries: List<MealEntry>, preferences: UserPreferences?): String =
        buildString {
            appendLine(contextBlock(preferences, emptyList()))
            appendLine("Logged meals this week:")
            entries.forEach { e ->
                val a = e.analysis
                appendLine(
                    "- ${e.entryDate} ${e.mealType.label}: ${e.mealName.ifBlank { a?.detectedMealName ?: "meal" }}" +
                        (a?.let {
                            " [protein=${it.proteinStatus}, fibre=${it.fibreStatus}, healthyFat=${it.healthyFatStatus}]"
                        } ?: "")
                )
            }
        }
}
