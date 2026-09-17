package com.abbeysbite.app

import com.abbeysbite.app.ai.RecipeSearchIntent
import com.abbeysbite.app.data.model.Recipe
import com.abbeysbite.app.data.repository.RecipeFiltering
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecipeFilteringTest {

    private val paneerWrap = Recipe(
        id = "r1", title = "Spicy paneer wraps",
        description = "Charred paneer in chili yogurt",
        prepMinutes = 8, cookMinutes = 7,
        tags = listOf("quick", "spicy"), dietTags = listOf("vegetarian"),
        cuisine = "Indian",
    )

    private val rajma = Recipe(
        id = "r2", title = "Slow Sunday rajma",
        description = "Kidney beans simmered slow",
        prepMinutes = 15, cookMinutes = 45,
        tags = listOf("budget", "dinner"), dietTags = listOf("vegetarian", "vegan"),
    )

    @Test
    fun timeLimitFiltersSlowRecipes() {
        val intent = RecipeSearchIntent(maxTotalMinutes = 15)
        assertTrue(RecipeFiltering.matchesIntent(emptyList(), paneerWrap, intent))
        assertFalse(RecipeFiltering.matchesIntent(emptyList(), rajma, intent))
    }

    @Test
    fun dietTagsMustAllMatch() {
        val vegan = RecipeSearchIntent(dietTags = listOf("vegan"))
        assertFalse(RecipeFiltering.matchesIntent(emptyList(), paneerWrap, vegan))
        assertTrue(RecipeFiltering.matchesIntent(emptyList(), rajma, vegan))
    }

    @Test
    fun budgetRequiresBudgetTag() {
        val intent = RecipeSearchIntent(budget = true)
        assertFalse(RecipeFiltering.matchesIntent(emptyList(), paneerWrap, intent))
        assertTrue(RecipeFiltering.matchesIntent(emptyList(), rajma, intent))
    }

    @Test
    fun mustUseIngredientsMatchAgainstIngredientListAndTitle() {
        val intent = RecipeSearchIntent(mustUseIngredients = listOf("paneer"))
        assertTrue(RecipeFiltering.matchesIntent(emptyList(), paneerWrap, intent)) // via title
        assertTrue(
            RecipeFiltering.matchesIntent(listOf("Paneer, cubed"), rajma.copy(title = "Mystery bowl"), intent)
        )
        assertFalse(RecipeFiltering.matchesIntent(listOf("Beans"), rajma, intent))
    }

    @Test
    fun keywordsMatchTitleDescriptionCuisineTags() {
        val intent = RecipeSearchIntent(keywords = listOf("spicy"))
        assertTrue(RecipeFiltering.matchesIntent(emptyList(), paneerWrap, intent))
        assertFalse(RecipeFiltering.matchesIntent(emptyList(), rajma, intent))
    }

    @Test
    fun keywordSearchMatchesAllTerms() {
        assertTrue(RecipeFiltering.matchesKeyword(paneerWrap, "spicy paneer"))
        assertFalse(RecipeFiltering.matchesKeyword(rajma, "spicy paneer"))
        assertTrue(RecipeFiltering.matchesKeyword(rajma, ""))
    }

    @Test
    fun emptyIntentMatchesEverything() {
        val intent = RecipeSearchIntent()
        assertTrue(RecipeFiltering.matchesIntent(emptyList(), paneerWrap, intent))
        assertTrue(RecipeFiltering.matchesIntent(emptyList(), rajma, intent))
    }
}
