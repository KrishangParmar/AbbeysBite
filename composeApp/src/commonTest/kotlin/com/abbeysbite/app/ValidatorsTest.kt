package com.abbeysbite.app

import com.abbeysbite.app.data.model.Recipe
import com.abbeysbite.app.data.model.RecipeIngredient
import com.abbeysbite.app.data.model.RecipeStep
import com.abbeysbite.app.domain.Validators
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ValidatorsTest {

    // ------------------------------------------------------------ YouTube URLs

    @Test
    fun acceptsStandardWatchUrl() {
        assertEquals("dQw4w9WgXcQ", Validators.youtubeVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test
    fun acceptsShortUrl() {
        assertEquals("dQw4w9WgXcQ", Validators.youtubeVideoId("https://youtu.be/dQw4w9WgXcQ"))
    }

    @Test
    fun acceptsShortsAndEmbedUrls() {
        assertEquals("dQw4w9WgXcQ", Validators.youtubeVideoId("https://youtube.com/shorts/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", Validators.youtubeVideoId("https://www.youtube.com/embed/dQw4w9WgXcQ"))
    }

    @Test
    fun acceptsWatchUrlWithExtraParams() {
        assertEquals(
            "dQw4w9WgXcQ",
            Validators.youtubeVideoId("https://www.youtube.com/watch?t=42&v=dQw4w9WgXcQ&list=PL123"),
        )
    }

    @Test
    fun rejectsNonYoutubeUrls() {
        assertNull(Validators.youtubeVideoId("https://vimeo.com/12345"))
        assertNull(Validators.youtubeVideoId("https://example.com/watch?v=dQw4w9WgXcQ"))
        assertNull(Validators.youtubeVideoId("not a url"))
        assertNull(Validators.youtubeVideoId(null))
        assertNull(Validators.youtubeVideoId(""))
    }

    @Test
    fun rejectsMalformedVideoIds() {
        assertNull(Validators.youtubeVideoId("https://youtu.be/short"))
    }

    // ------------------------------------------------------------ Usernames

    @Test
    fun validUsernames() {
        assertTrue(Validators.isValidUsername("abbey_fan_01"))
        assertTrue(Validators.isValidUsername("kim"))
    }

    @Test
    fun invalidUsernames() {
        assertFalse(Validators.isValidUsername("ab"))
        assertFalse(Validators.isValidUsername("Has Spaces"))
        assertFalse(Validators.isValidUsername("UPPER"))
        assertFalse(Validators.isValidUsername("way_too_long_username_beyond_24"))
        assertFalse(Validators.isValidUsername("emoji😊"))
    }

    // ------------------------------------------------------------ Recipes

    private fun recipe(
        title: String = "Paneer bhurji",
        youtubeUrl: String? = null,
        servings: Int = 2,
    ) = Recipe(title = title, youtubeUrl = youtubeUrl, servings = servings)

    private val oneIngredient = listOf(RecipeIngredient(name = "Paneer", quantity = "200", unit = "g"))
    private val oneStep = listOf(RecipeStep(stepNumber = 1, instruction = "Crumble and cook."))

    @Test
    fun validRecipePasses() {
        val v = Validators.validateRecipeForPublish(recipe(), oneIngredient, oneStep, hasCoverImage = true)
        assertTrue(v.isValid, "expected valid but got: ${v.errors}")
    }

    @Test
    fun recipeNeedsTitleIngredientsStepsAndCover() {
        val v = Validators.validateRecipeForPublish(
            recipe(title = "ab"), emptyList(), emptyList(), hasCoverImage = false,
        )
        assertEquals(4, v.errors.size)
    }

    @Test
    fun recipeRejectsInvalidYoutubeUrl() {
        val v = Validators.validateRecipeForPublish(
            recipe(youtubeUrl = "https://example.com/nope"), oneIngredient, oneStep, true,
        )
        assertFalse(v.isValid)
    }

    @Test
    fun recipeAllowsBlankYoutubeUrl() {
        val v = Validators.validateRecipeForPublish(
            recipe(youtubeUrl = ""), oneIngredient, oneStep, true,
        )
        assertTrue(v.isValid)
    }

    @Test
    fun recipeRejectsSillyServings() {
        assertFalse(
            Validators.validateRecipeForPublish(recipe(servings = 0), oneIngredient, oneStep, true).isValid
        )
    }
}
