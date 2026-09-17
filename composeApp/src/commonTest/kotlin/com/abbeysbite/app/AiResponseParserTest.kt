package com.abbeysbite.app

import com.abbeysbite.app.ai.AiResponseParser
import com.abbeysbite.app.ai.RecipeSearchIntent
import com.abbeysbite.app.data.model.AdditionCategory
import com.abbeysbite.app.data.model.AdditionEffort
import com.abbeysbite.app.data.model.NutrientStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiResponseParserTest {

    private val validAnalysisJson = """
        {
          "detected_meal_name": "Buttered toast",
          "confidence": 0.87,
          "components": [{"name": "Toast", "confidence": 0.95}, {"name": "Butter", "confidence": 0.8}],
          "protein_status": "could_add",
          "protein_sources": [],
          "fibre_status": "could_add",
          "fibre_sources": [],
          "healthy_fat_status": "present",
          "healthy_fat_sources": ["Butter"],
          "suggested_additions": [
            {"name": "Egg", "category": "protein", "reason": "Pairs well with toast", "effort": "low",
             "pantry_match": true, "optional_substitution": "Greek yogurt"}
          ],
          "notes": "Nice start."
        }
    """.trimIndent()

    @Test
    fun parsesCleanAnalysisJson() {
        val result = AiResponseParser.parseMealAnalysis(validAnalysisJson)
        val analysis = result.getOrNull()
        assertNotNull(analysis)
        assertEquals("Buttered toast", analysis.detectedMealName)
        assertEquals(NutrientStatus.COULD_ADD, analysis.proteinStatus)
        assertEquals(NutrientStatus.PRESENT, analysis.healthyFatStatus)
        assertEquals(1, analysis.suggestedAdditions.size)
        val addition = analysis.suggestedAdditions.first()
        assertEquals(AdditionCategory.PROTEIN, addition.category)
        assertEquals(AdditionEffort.LOW, addition.effort)
        assertTrue(addition.pantryMatch)
        assertEquals("Greek yogurt", addition.optionalSubstitution)
    }

    @Test
    fun parsesJsonWrappedInMarkdownFences() {
        val fenced = "Here is your analysis:\n```json\n$validAnalysisJson\n```\nEnjoy!"
        val result = AiResponseParser.parseMealAnalysis(fenced)
        assertEquals("Buttered toast", result.getOrNull()?.detectedMealName)
    }

    @Test
    fun toleratesUnknownKeysAndMissingOptionalFields() {
        val minimal = """{"detected_meal_name": "Dal rice", "mystery_field": 42}"""
        val analysis = AiResponseParser.parseMealAnalysis(minimal).getOrNull()
        assertNotNull(analysis)
        assertEquals("Dal rice", analysis.detectedMealName)
        assertEquals(NutrientStatus.UNCERTAIN, analysis.proteinStatus)
        assertTrue(analysis.suggestedAdditions.isEmpty())
    }

    @Test
    fun rejectsResponsesWithoutAnyJson() {
        val result = AiResponseParser.parseMealAnalysis("Sorry, I can't help with that.")
        assertFalse(result.isSuccess)
    }

    @Test
    fun rejectsEmptyAnalysis() {
        val result = AiResponseParser.parseMealAnalysis("""{"confidence": 0.5}""")
        assertFalse(result.isSuccess)
    }

    @Test
    fun capsSuggestionsAtFour() {
        val many = (1..8).joinToString(",") {
            """{"name": "Item $it", "category": "general", "reason": "r", "effort": "low"}"""
        }
        val json = """{"detected_meal_name": "Bowl", "suggested_additions": [$many]}"""
        assertEquals(4, AiResponseParser.parseMealAnalysis(json).getOrNull()?.suggestedAdditions?.size)
    }

    @Test
    fun extractsBalancedJsonWithNestedBracesInStrings() {
        val tricky = """prefix {"detected_meal_name": "Weird {name}", "notes": "has } brace"} suffix"""
        val extracted = AiResponseParser.extractJsonObject(tricky)
        assertNotNull(extracted)
        assertEquals("Weird {name}", AiResponseParser.parseMealAnalysis(tricky).getOrNull()?.detectedMealName)
    }

    @Test
    fun nutrientStatusLenientMapping() {
        assertEquals(NutrientStatus.PRESENT, NutrientStatus.fromRaw("present"))
        assertEquals(NutrientStatus.PRESENT, NutrientStatus.fromRaw("YES"))
        assertEquals(NutrientStatus.COULD_ADD, NutrientStatus.fromRaw("could_add"))
        assertEquals(NutrientStatus.COULD_ADD, NutrientStatus.fromRaw("missing"))
        assertEquals(NutrientStatus.UNCERTAIN, NutrientStatus.fromRaw("banana"))
        assertEquals(NutrientStatus.UNCERTAIN, NutrientStatus.fromRaw(null))
    }

    @Test
    fun parsesRecipeSearchIntent() {
        val raw = """{"keywords": ["spicy", "paneer"], "max_total_minutes": 15,
            "diet_tags": ["vegetarian"], "must_use_ingredients": ["paneer"], "budget": false}"""
        val intent = AiResponseParser.decode<RecipeSearchIntent>(raw).getOrNull()
        assertNotNull(intent)
        assertEquals(15, intent.maxTotalMinutes)
        assertEquals(listOf("vegetarian"), intent.dietTags)
    }

    @Test
    fun weeklyInsightCapsObservationsAndIdeas() {
        val raw = """{"observations": ["a","b","c","d","e"], "ideas": ["x","y","z"]}"""
        val insight = AiResponseParser.parseWeeklyInsight(raw).getOrNull()
        assertNotNull(insight)
        assertEquals(3, insight.observations.size)
        assertEquals(2, insight.ideas.size)
    }

    @Test
    fun extractReturnsNullForUnbalancedJson() {
        assertNull(AiResponseParser.extractJsonObject("""{"detected_meal_name": "oops" """))
    }
}
