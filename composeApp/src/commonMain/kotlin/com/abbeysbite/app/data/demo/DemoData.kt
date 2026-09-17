package com.abbeysbite.app.data.demo

import com.abbeysbite.app.data.model.Profile
import com.abbeysbite.app.data.model.Recipe
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import com.abbeysbite.app.data.model.RecipeDetail
import com.abbeysbite.app.data.model.RecipeDifficulty
import com.abbeysbite.app.data.model.RecipeIngredient
import com.abbeysbite.app.data.model.RecipeStatus
import com.abbeysbite.app.data.model.RecipeStep

/**
 * DEBUG-only demo fixtures. Used by the demo repositories when Supabase isn't
 * configured, and by the seed tooling. Production builds with real credentials
 * never touch this data.
 */
object DemoData {

    val profiles = listOf(
        Profile(id = "demo-profile-1", username = "mira_cooks", displayName = "Mira", bio = "Weeknight cooking, mostly one pan."),
        Profile(id = "demo-profile-2", username = "dev_eats", displayName = "Dev", bio = "Hostel cooking hacks."),
        Profile(id = "demo-profile-3", username = "anitas_kitchen", displayName = "Anita", bio = "Family recipes, simplified."),
        Profile(id = "demo-profile-4", username = "sam_spices", displayName = "Sam", bio = "Spice-forward, budget-friendly."),
    )

    private fun recipe(
        id: String,
        author: String,
        title: String,
        description: String,
        prep: Int,
        cook: Int,
        difficulty: RecipeDifficulty,
        servings: Int,
        cuisine: String,
        tags: List<String>,
        dietTags: List<String>,
        likes: Int,
        youtube: String? = null,
    ) = Recipe(
        id = id, authorId = author, title = title, description = description,
    	prepMinutes = prep, cookMinutes = cook, difficulty = difficulty, servings = servings,
        cuisine = cuisine, tags = tags, dietTags = dietTags, youtubeUrl = youtube,
        status = RecipeStatus.PUBLISHED, likeCount = likes,
        createdAt = "2026-08-20T10:00:00Z",
    )

    val recipes = listOf(
        recipe(
            "demo-recipe-1", "demo-profile-1", "10-minute masala scrambled eggs",
            "Soft scrambled eggs with onion, tomato and a little garam masala. Protein-packed and endlessly forgiving.",
            5, 5, RecipeDifficulty.EASY, 2, "Indian",
            listOf("quick", "budget", "breakfast"), emptyList(), 128,
        ),
        recipe(
            "demo-recipe-2", "demo-profile-2", "Microwave mug dal",
            "Actual dal in a hostel mug — moong dal, water, turmeric, salt, microwave. Top with whatever crunch you have.",
            2, 12, RecipeDifficulty.EASY, 1, "Indian",
            listOf("budget", "hostel", "microwave"), listOf("vegetarian", "vegan"), 96,
        ),
        recipe(
            "demo-recipe-3", "demo-profile-3", "Paneer bhurji toast",
            "Crumbled paneer with peppers piled onto hot buttered toast. A five-minute upgrade that makes toast a meal.",
            5, 8, RecipeDifficulty.EASY, 2, "Indian",
            listOf("quick", "breakfast"), listOf("vegetarian"), 210,
        ),
        recipe(
            "demo-recipe-4", "demo-profile-4", "Peanut butter banana overnight oats",
            "Stir, refrigerate, eat. Fibre from oats, protein and healthy fats from peanut butter — breakfast does itself.",
            5, 0, RecipeDifficulty.EASY, 1, "Global",
            listOf("breakfast", "no-cook", "budget"), listOf("vegetarian"), 173,
        ),
        recipe(
            "demo-recipe-5", "demo-profile-1", "One-pan lemon garlic chickpeas",
            "Crispy chickpeas with lemon, garlic and greens. Works as a side or a whole lazy dinner with bread.",
            5, 15, RecipeDifficulty.EASY, 2, "Mediterranean",
            listOf("quick", "budget", "dinner"), listOf("vegetarian", "vegan"), 84,
        ),
        recipe(
            "demo-recipe-6", "demo-profile-3", "Weeknight veggie fried rice",
            "Day-old rice, frozen vegetables, eggs and soy sauce. Dinner in 12 minutes, and it clears the fridge.",
            5, 12, RecipeDifficulty.EASY, 3, "Asian",
            listOf("quick", "dinner", "leftovers"), listOf("vegetarian"), 152,
        ),
        recipe(
            "demo-recipe-7", "demo-profile-4", "Spicy paneer wraps",
            "Charred paneer tossed in chili-yogurt, rolled into warm rotis with cucumber. Fifteen minutes flat.",
            8, 7, RecipeDifficulty.MEDIUM, 2, "Indian",
            listOf("quick", "lunch", "spicy"), listOf("vegetarian"), 199,
        ),
        recipe(
            "demo-recipe-8", "demo-profile-2", "3-ingredient yogurt parfait",
            "Yogurt, chopped fruit, and any crunchy topping. Zero cooking, works with any fruit you have around.",
            4, 0, RecipeDifficulty.EASY, 1, "Global",
            listOf("no-cook", "snack", "budget"), listOf("vegetarian"), 67,
        ),
        recipe(
            "demo-recipe-9", "demo-profile-1", "Sheet-pan honey chili salmon",
            "Salmon and vegetables roasted together with a honey-chili glaze. Healthy fats without any fuss.",
            10, 15, RecipeDifficulty.MEDIUM, 2, "Global",
            listOf("dinner"), emptyList(), 141,
        ),
        recipe(
            "demo-recipe-10", "demo-profile-3", "Slow Sunday rajma",
            "Kidney beans simmered with onion, tomato and warm spices. Make a pot, eat well for days.",
            15, 45, RecipeDifficulty.INVOLVED, 4, "Indian",
            listOf("dinner", "meal-prep", "budget"), listOf("vegetarian", "vegan"), 233,
        ),
    )

    private val ingredientsByRecipe: Map<String, List<String>> = mapOf(
        "demo-recipe-1" to listOf("Eggs|4|", "Onion, finely chopped|1|small", "Tomato, chopped|1|", "Garam masala|0.5|tsp", "Butter|1|tbsp", "Salt|to taste|"),
        "demo-recipe-2" to listOf("Moong dal, rinsed|0.25|cup", "Water|1|cup", "Turmeric|0.25|tsp", "Salt|to taste|"),
        "demo-recipe-3" to listOf("Paneer, crumbled|150|g", "Bread slices|4|", "Capsicum, diced|0.5|", "Butter|1|tbsp", "Chili powder|0.25|tsp"),
        "demo-recipe-4" to listOf("Rolled oats|0.5|cup", "Milk or water|0.75|cup", "Peanut butter|1.5|tbsp", "Banana, sliced|1|"),
        "demo-recipe-5" to listOf("Cooked chickpeas|1.5|cups", "Garlic cloves, sliced|3|", "Lemon|0.5|", "Olive oil|1.5|tbsp", "Spinach or any greens|2|handfuls"),
        "demo-recipe-6" to listOf("Cooked rice, cooled|2|cups", "Mixed frozen vegetables|1|cup", "Eggs|2|", "Soy sauce|1.5|tbsp", "Oil|1|tbsp"),
        "demo-recipe-7" to listOf("Paneer, cubed|200|g", "Thick yogurt|3|tbsp", "Chili powder|0.5|tsp", "Rotis or wraps|2|", "Cucumber, sliced|0.5|"),
        "demo-recipe-8" to listOf("Thick yogurt|1|cup", "Seasonal fruit, chopped|1|cup", "Granola, nuts or seeds|3|tbsp"),
        "demo-recipe-9" to listOf("Salmon fillets|2|", "Broccoli florets|2|cups", "Honey|1|tbsp", "Chili flakes|0.5|tsp", "Olive oil|1|tbsp"),
        "demo-recipe-10" to listOf("Rajma, soaked overnight|1|cup", "Onion, sliced|2|", "Tomatoes, pureed|3|", "Ginger-garlic paste|1|tbsp", "Rajma masala|2|tsp", "Rice, to serve|2|cups"),
    )

    private val stepsByRecipe: Map<String, List<String>> = mapOf(
        "demo-recipe-1" to listOf(
            "Melt butter in a pan over medium heat and soften the onion for 2 minutes.",
            "Add tomato and garam masala; cook until jammy.",
            "Whisk the eggs with salt, pour in, and stir gently until just set.",
        ),
        "demo-recipe-2" to listOf(
            "Combine dal, water, turmeric and salt in a large microwave-safe mug.",
            "Microwave in 4-minute bursts, stirring between, until the dal is soft (about 12 minutes).",
            "Rest for a minute, season, and top with anything crunchy.",
        ),
        "demo-recipe-3" to listOf(
            "Melt butter and sauté capsicum for 2 minutes.",
            "Add crumbled paneer, chili powder and salt; toss for 3–4 minutes.",
            "Toast the bread, pile the bhurji on top and eat immediately.",
        ),
        "demo-recipe-4" to listOf(
            "Stir oats, milk and peanut butter together in a jar.",
            "Refrigerate overnight (or at least 4 hours).",
            "Top with banana slices before eating.",
        ),
        "demo-recipe-5" to listOf(
            "Heat olive oil and fry garlic until fragrant.",
            "Add chickpeas and cook undisturbed until they crisp in spots.",
            "Wilt in the greens, squeeze over the lemon, season and serve.",
        ),
        "demo-recipe-6" to listOf(
            "Scramble the eggs in a hot pan and set aside.",
            "Stir-fry the vegetables for 3–4 minutes, then add rice and soy sauce.",
            "Toss on high heat, fold the eggs back in and serve.",
        ),
        "demo-recipe-7" to listOf(
            "Toss paneer with yogurt, chili powder and salt.",
            "Sear in a hot pan until charred at the edges.",
            "Roll into warm rotis with cucumber and extra yogurt.",
        ),
        "demo-recipe-8" to listOf(
            "Spoon half the yogurt into a glass.",
            "Layer in fruit, then the rest of the yogurt.",
            "Finish with your crunchy topping.",
        ),
        "demo-recipe-9" to listOf(
            "Heat the oven to 220°C and line a tray.",
            "Toss broccoli in oil; place salmon alongside and brush with honey-chili glaze.",
            "Roast 12–15 minutes until the salmon flakes easily.",
        ),
        "demo-recipe-10" to listOf(
            "Pressure-cook soaked rajma until tender.",
            "Fry onions until deep gold, add ginger-garlic and tomato puree; cook down.",
            "Add spices and rajma with its liquid; simmer 20 minutes until creamy. Serve over rice.",
        ),
    )

    /** DEBUG-only sample journal covering the previous three days. */
    fun demoJournal(today: kotlinx.datetime.LocalDate): List<com.abbeysbite.app.data.model.MealEntry> {
        fun entry(
            daysAgo: Int,
            type: com.abbeysbite.app.data.model.MealType,
            name: String,
            protein: com.abbeysbite.app.data.model.NutrientStatus,
            fibre: com.abbeysbite.app.data.model.NutrientStatus,
            fat: com.abbeysbite.app.data.model.NutrientStatus,
            components: List<String>,
        ): com.abbeysbite.app.data.model.MealEntry {
            val date = today.minus(daysAgo, DateTimeUnit.DAY)
            return com.abbeysbite.app.data.model.MealEntry(
                id = "demo-journal-$daysAgo-${type.name}",
                mealType = type,
                mealName = name,
                eatenAt = "${date}T12:00:00Z",
                entryDate = date.toString(),
                analysis = com.abbeysbite.app.data.model.MealAnalysis(
                    detectedMealName = name,
                    confidence = 0.9,
                    components = components.map { com.abbeysbite.app.data.model.MealComponent(it) },
                    proteinStatus = protein,
                    fibreStatus = fibre,
                    healthyFatStatus = fat,
                ),
            )
        }

        val present = com.abbeysbite.app.data.model.NutrientStatus.PRESENT
        val couldAdd = com.abbeysbite.app.data.model.NutrientStatus.COULD_ADD
        return listOf(
            entry(1, com.abbeysbite.app.data.model.MealType.BREAKFAST,
                "Peanut butter banana oats", present, present, present,
                listOf("Oats", "Peanut butter", "Banana")),
            entry(1, com.abbeysbite.app.data.model.MealType.DINNER,
                "Veggie fried rice", couldAdd, present, couldAdd,
                listOf("Rice", "Mixed vegetables")),
            entry(2, com.abbeysbite.app.data.model.MealType.LUNCH,
                "Paneer wrap", present, couldAdd, present,
                listOf("Paneer", "Roti", "Yogurt")),
            entry(3, com.abbeysbite.app.data.model.MealType.BREAKFAST,
                "Buttered toast", couldAdd, couldAdd, present,
                listOf("Toast", "Butter")),
        )
    }

    fun detailFor(recipe: Recipe): RecipeDetail = RecipeDetail(
        recipe = recipe,
        ingredients = (ingredientsByRecipe[recipe.id] ?: emptyList()).mapIndexed { i, raw ->
            val parts = raw.split("|")
            RecipeIngredient(
                id = "${recipe.id}-ing-$i",
                recipeId = recipe.id,
                name = parts[0],
                quantity = parts.getOrNull(1)?.takeIf { it.isNotBlank() },
                unit = parts.getOrNull(2)?.takeIf { it.isNotBlank() },
                sortOrder = i,
            )
        },
        steps = (stepsByRecipe[recipe.id] ?: emptyList()).mapIndexed { i, instruction ->
            RecipeStep(
                id = "${recipe.id}-step-$i",
                recipeId = recipe.id,
                stepNumber = i + 1,
                instruction = instruction,
            )
        },
        author = profiles.find { it.id == recipe.authorId },
    )
}
