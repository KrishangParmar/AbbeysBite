package com.abbeysbite.app.domain

import com.abbeysbite.app.data.model.MealEntry
import com.abbeysbite.app.data.model.NourishmentSnapshot
import com.abbeysbite.app.data.model.NutrientStatus
import com.abbeysbite.app.data.model.RhythmDay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/**
 * Pure aggregation logic for the Journal tab — rhythm strip, nourishment
 * snapshot and plant-variety counting. Fully unit-tested; no IO.
 */
object JournalAggregator {

    /** Plant-ish keywords used for the approximate plant-variety stat. */
    private val plantKeywords = setOf(
        "apple", "banana", "berry", "berries", "orange", "mango", "grape", "melon",
        "spinach", "kale", "lettuce", "greens", "salad", "broccoli", "cauliflower",
        "carrot", "cucumber", "tomato", "pepper", "onion", "garlic", "potato",
        "sweet potato", "peas", "beans", "lentil", "dal", "chickpea", "chana",
        "oats", "rice", "quinoa", "wheat", "millet", "corn", "avocado",
        "nut", "almond", "walnut", "peanut", "cashew", "seed", "chia", "flax",
        "mushroom", "pumpkin", "beet", "cabbage", "okra", "eggplant", "brinjal",
    )

    /**
     * Builds the 7-day rhythm strip ending at [today], Monday-first.
     * Missing days are simply unfilled — never styled as failures.
     */
    fun rhythmWeek(entries: List<MealEntry>, today: LocalDate): List<RhythmDay> {
        val monday = today.minus(
            (today.dayOfWeek.ordinal - DayOfWeek.MONDAY.ordinal + 7) % 7,
            DateTimeUnit.DAY,
        )
        val loggedDates = entries.mapNotNull { runCatching { LocalDate.parse(it.entryDate) }.getOrNull() }.toSet()
        return (0..6).map { offset ->
            val date = monday.plusDays(offset)
            RhythmDay(
                date = date,
                logged = date in loggedDates,
                isToday = date == today,
            )
        }
    }

    private fun LocalDate.plusDays(days: Int): LocalDate =
        this.minus(-days, DateTimeUnit.DAY)

    /** Count of days in the current rhythm week with at least one logged meal. */
    fun nourishingDaysCount(week: List<RhythmDay>): Int = week.count { it.logged }

    fun snapshot(entries: List<MealEntry>): NourishmentSnapshot {
        val withAnalysis = entries.mapNotNull { it.analysis }
        return NourishmentSnapshot(
            mealsLogged = entries.size,
            daysWithMeals = entries.map { it.entryDate }.distinct().count { it.isNotBlank() },
            proteinPresentCount = withAnalysis.count { it.proteinStatus == NutrientStatus.PRESENT },
            fibrePresentCount = withAnalysis.count { it.fibreStatus == NutrientStatus.PRESENT },
            healthyFatPresentCount = withAnalysis.count { it.healthyFatStatus == NutrientStatus.PRESENT },
            plantVariety = plantVariety(entries),
        )
    }

    /** Approximate count of distinct plant foods across the entries. */
    fun plantVariety(entries: List<MealEntry>): Int {
        val componentNames = entries
            .flatMap { entry ->
                (entry.analysis?.components?.map { it.name } ?: emptyList()) + entry.mealName
            }
            .map { it.lowercase() }
        return plantKeywords.count { keyword ->
            componentNames.any { it.contains(keyword) }
        }
    }

    /**
     * Longest run of consecutive logged days ending at (and including) today
     * or yesterday — a gentle streak, shown only as a secondary stat.
     */
    fun loggingStreak(allDates: List<LocalDate>, today: LocalDate): Int {
        if (allDates.isEmpty()) return 0
        val dates = allDates.toSet()
        var anchor = when {
            today in dates -> today
            today.minus(1, DateTimeUnit.DAY) in dates -> today.minus(1, DateTimeUnit.DAY)
            else -> return 0
        }
        var streak = 0
        while (anchor in dates) {
            streak++
            anchor = anchor.minus(1, DateTimeUnit.DAY)
        }
        return streak
    }
}
