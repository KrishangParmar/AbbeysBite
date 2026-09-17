package com.abbeysbite.app.ai

import com.abbeysbite.app.core.util.AppResult
import com.abbeysbite.app.data.model.AdditionCategory
import com.abbeysbite.app.data.model.AdditionEffort
import com.abbeysbite.app.data.model.DietPreference
import com.abbeysbite.app.data.model.MealAnalysis
import com.abbeysbite.app.data.model.MealComponent
import com.abbeysbite.app.data.model.MealCorrection
import com.abbeysbite.app.data.model.MealEntry
import com.abbeysbite.app.data.model.NutrientStatus
import com.abbeysbite.app.data.model.PantryItem
import com.abbeysbite.app.data.model.SuggestedAddition
import com.abbeysbite.app.data.model.UserPreferences
import com.abbeysbite.app.data.model.WeeklyInsight
import kotlinx.coroutines.delay

/**
 * Deterministic offline [AiProvider] used when no gateway credentials are
 * configured (fresh checkout, tests, demo mode). Produces sensible, on-brand
 * results so the whole product is explorable without any backend.
 */
class MockAiProvider : AiProvider {

    override suspend fun analyzeMeal(
        imageBytes: ByteArray?,
        description: String?,
        preferences: UserPreferences?,
        pantry: List<PantryItem>,
        correction: MealCorrection?,
        previousAnalysis: MealAnalysis?,
    ): AppResult<MealAnalysis> {
        delay(1400) // let the scanning animation breathe
        val desc = correction?.renamedMeal ?: description ?: "Toast with butter"
        val lower = desc.lowercase()
        val vegetarian = preferences?.dietPreference in
            setOf(DietPreference.VEGETARIAN, DietPreference.VEGAN)
        val vegan = preferences?.dietPreference == DietPreference.VEGAN
        val avoid = preferences?.avoidFoods?.map { it.lowercase() } ?: emptyList()
        val pantryNames = pantry.map { it.name.lowercase() }

        fun allowed(name: String) = avoid.none { name.lowercase().contains(it) }
        fun inPantry(name: String) = pantryNames.any { p ->
            p.contains(name.lowercase()) || name.lowercase().contains(p)
        }
        fun addition(
            name: String,
            category: AdditionCategory,
            reason: String,
            effort: AdditionEffort = AdditionEffort.LOW,
            substitution: String? = null,
        ) = SuggestedAddition(
            name = name, category = category, reason = reason, effort = effort,
            pantryMatch = inPantry(name), optionalSubstitution = substitution,
        )

        val baseComponents = when {
            lower.contains("toast") || lower.contains("bread") -> listOf("Toast", "Butter")
            lower.contains("rice") -> listOf("Steamed rice")
            lower.contains("pasta") || lower.contains("noodle") -> listOf("Pasta", "Sauce")
            lower.contains("salad") -> listOf("Mixed greens", "Dressing")
            lower.contains("oats") || lower.contains("porridge") -> listOf("Oats", "Milk")
            else -> listOf(desc.split(",", " and ").first().trim().replaceFirstChar { it.uppercase() })
        }.toMutableList()
        correction?.addedComponents?.forEach { baseComponents.add(it) }
        correction?.removedComponents?.forEach { removed ->
            baseComponents.removeAll { it.equals(removed, ignoreCase = true) }
        }

        val hasProtein = baseComponents.any {
            it.lowercase().let { c ->
                c.contains("egg") || c.contains("chicken") || c.contains("paneer") ||
                    c.contains("dal") || c.contains("yogurt") || c.contains("tofu")
            }
        }
        val hasFibre = baseComponents.any {
            it.lowercase().let { c ->
                c.contains("greens") || c.contains("salad") || c.contains("oats") ||
                    c.contains("veg") || c.contains("bean")
            }
        }
        val hasFat = baseComponents.any {
            it.lowercase().let { c ->
                c.contains("butter") || c.contains("avocado") || c.contains("nut") ||
                    c.contains("seed") || c.contains("olive")
            }
        }

        val suggestions = buildList {
            if (!hasProtein) {
                if (!vegan && allowed("egg")) add(
                    addition("A fried or boiled egg", AdditionCategory.PROTEIN,
                        "Quick protein that pairs naturally with this.", AdditionEffort.LOW,
                        substitution = if (vegetarian) "Greek yogurt on the side" else null)
                )
                if (allowed("peanut butter")) add(
                    addition("Peanut butter", AdditionCategory.PROTEIN,
                        "A spoonful adds protein and staying power.", AdditionEffort.NONE,
                        substitution = "Any nut or seed butter")
                )
                if (vegan && allowed("tofu")) add(
                    addition("Pan-seared tofu cubes", AdditionCategory.PROTEIN,
                        "Mild flavor, soaks up whatever you season it with.", AdditionEffort.MEDIUM)
                )
            }
            if (!hasFibre && allowed("banana")) add(
                addition("Sliced banana or any fruit", AdditionCategory.FIBRE,
                    "Easy fibre with no prep beyond slicing.", AdditionEffort.NONE)
            )
            if (!hasFibre && allowed("cucumber")) add(
                addition("Cucumber or carrot sticks on the side", AdditionCategory.FIBRE,
                    "Crunchy contrast plus fibre in under a minute.", AdditionEffort.NONE)
            )
            if (!hasFat && allowed("seeds")) add(
                addition("A sprinkle of mixed seeds", AdditionCategory.HEALTHY_FAT,
                    "Adds crunch and healthy fats without changing the flavor.", AdditionEffort.NONE,
                    substitution = "Chopped nuts")
            )
            if (!hasFat && allowed("avocado")) add(
                addition("A few slices of avocado", AdditionCategory.HEALTHY_FAT,
                    "Creamy healthy fat that makes this more satisfying.", AdditionEffort.LOW)
            )
        }.take(4)

        return AppResult.Success(
            MealAnalysis(
                detectedMealName = desc.replaceFirstChar { it.uppercase() },
                confidence = if (correction != null) 0.95 else 0.82,
                components = baseComponents.map {
                    MealComponent(it, 0.9, userAdded = correction?.addedComponents?.contains(it) == true)
                },
                proteinStatus = if (hasProtein) NutrientStatus.PRESENT else NutrientStatus.COULD_ADD,
                proteinSources = baseComponents.filter { hasProtein }.take(2),
                fibreStatus = if (hasFibre) NutrientStatus.PRESENT else NutrientStatus.COULD_ADD,
                fibreSources = baseComponents.filter { hasFibre }.take(2),
                healthyFatStatus = if (hasFat) NutrientStatus.PRESENT else NutrientStatus.COULD_ADD,
                healthyFatSources = baseComponents.filter { hasFat }.take(2),
                suggestedAdditions = suggestions,
                notes = "Looks tasty. A small addition or two could make it even more satisfying.",
            )
        )
    }

    override suspend fun chat(context: MealChatContext, userMessage: String): AppResult<String> {
        delay(700)
        val lower = userMessage.lowercase()
        val reply = when {
            lower.contains("egg") && lower.contains("cheese") ->
                "Nice — scramble the eggs with a little cheese folded in at the end. " +
                    "It takes about three minutes and adds solid protein to your plate."
            lower.contains("vegetarian") || lower.contains("vegan") ->
                "Got it — I’ll keep suggestions plant-forward. Paneer, dal, beans, tofu and Greek yogurt " +
                    "are your easiest protein adds here."
            lower.contains("hostel") || lower.contains("microwave") ->
                "No stove, no problem. Try microwave-scrambled eggs in a mug (60–90 seconds), " +
                    "or stir peanut butter into whatever you have."
            lower.contains("minute") || lower.contains("time") ->
                "Fastest options: a spoon of peanut butter, a handful of nuts, or plain yogurt on the side. " +
                    "Zero cooking, under a minute."
            lower.contains("filling") ->
                "To make it more filling, add one protein (egg, yogurt, paneer) and one fibre " +
                    "(fruit, cucumber, or a handful of beans). That combination keeps you satisfied longest."
            lower.contains("swap") || lower.contains("instead") ->
                "Absolutely — paneer works well in place of yogurt here. Similar protein, and it holds up " +
                    "better if the dish is warm."
            else ->
                "Good question. With what you have, I’d add one quick protein and something crunchy for fibre — " +
                    "want me to pick the two easiest options?"
        }
        return AppResult.Success(reply)
    }

    override suspend fun parseRecipeQuery(query: String): AppResult<RecipeSearchIntent> {
        delay(300)
        val lower = query.lowercase()
        val minutes = Regex("(\\d{1,3})\\s*(?:min|minute)").find(lower)?.groupValues?.get(1)?.toIntOrNull()
        val dietTags = buildList {
            if (lower.contains("vegetarian")) add("vegetarian")
            if (lower.contains("vegan")) add("vegan")
        }
        val knownIngredients = listOf(
            "paneer", "egg", "eggs", "banana", "peanut butter", "yogurt", "rice", "oats",
            "chicken", "tofu", "beans", "cheese", "bread",
        )
        val must = knownIngredients.filter { lower.contains(it) }
        val stop = setOf(
            "something", "with", "in", "a", "an", "the", "i", "only", "have", "and",
            "minutes", "minute", "min", "cheap", "budget", "quick", "easy", "dinner",
            "breakfast", "lunch", "vegetarian", "vegan", "microwave",
        )
        val keywords = lower.split(Regex("[^a-z]+"))
            .filter { it.length > 2 && it !in stop && it !in must }
        return AppResult.Success(
            RecipeSearchIntent(
                keywords = keywords.take(5),
                maxTotalMinutes = minutes,
                dietTags = dietTags,
                mustUseIngredients = must,
                budget = lower.contains("cheap") || lower.contains("budget") || lower.contains("₹") ||
                    lower.contains("$"),
            )
        )
    }

    override suspend fun weeklyInsight(
        entries: List<MealEntry>,
        preferences: UserPreferences?,
    ): AppResult<WeeklyInsight> {
        delay(900)
        if (entries.isEmpty()) {
            return AppResult.Success(
                WeeklyInsight(
                    observations = listOf("A fresh week — nothing logged yet."),
                    ideas = listOf("Try logging just one meal a day to start seeing patterns."),
                )
            )
        }
        val byType = entries.groupBy { it.mealType }
        val proteinPresent = entries.count { it.analysis?.proteinStatus == NutrientStatus.PRESENT }
        val fatCouldAdd = entries.count { it.analysis?.healthyFatStatus == NutrientStatus.COULD_ADD }
        val observations = buildList {
            if (proteinPresent > 0 && proteinPresent * 2 >= entries.size) {
                add("Protein showed up in most of your meals this week — that consistency counts.")
            } else if (proteinPresent == 0) {
                add("This week could use a little more protein — one easy add per meal is plenty.")
            }
            byType.maxByOrNull { it.value.size }?.let { (type, list) ->
                add("${type.label} is your most consistently logged meal (${list.size} this week).")
            }
            if (fatCouldAdd > entries.size / 2) {
                add("Healthy fats are the easiest place to add something small this week.")
            }
        }.take(3)
        val ideas = buildList {
            add("Keeping nuts or seeds nearby makes an easy sprinkle-on addition.")
            if (entries.none { it.mealType == com.abbeysbite.app.data.model.MealType.BREAKFAST }) {
                add("Even a quick photo of breakfast helps your weekly picture.")
            } else {
                add("A side of fruit or cut vegetables is a low-effort fibre add.")
            }
        }.take(2)
        return AppResult.Success(WeeklyInsight(observations = observations, ideas = ideas))
    }
}
