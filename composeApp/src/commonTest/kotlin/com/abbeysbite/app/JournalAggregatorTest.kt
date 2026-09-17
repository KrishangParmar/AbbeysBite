package com.abbeysbite.app

import com.abbeysbite.app.data.model.MealAnalysis
import com.abbeysbite.app.data.model.MealComponent
import com.abbeysbite.app.data.model.MealEntry
import com.abbeysbite.app.data.model.MealType
import com.abbeysbite.app.data.model.NutrientStatus
import com.abbeysbite.app.domain.JournalAggregator
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JournalAggregatorTest {

    private fun entry(
        date: String,
        type: MealType = MealType.LUNCH,
        protein: NutrientStatus = NutrientStatus.UNCERTAIN,
        fibre: NutrientStatus = NutrientStatus.UNCERTAIN,
        fat: NutrientStatus = NutrientStatus.UNCERTAIN,
        components: List<String> = emptyList(),
    ) = MealEntry(
        id = date + type.name,
        mealType = type,
        mealName = "Meal",
        eatenAt = "${date}T12:00:00Z",
        entryDate = date,
        analysis = MealAnalysis(
            detectedMealName = "Meal",
            components = components.map { MealComponent(it) },
            proteinStatus = protein,
            fibreStatus = fibre,
            healthyFatStatus = fat,
        ),
    )

    // 2026-09-02 is a Wednesday
    private val wednesday = LocalDate(2026, 9, 2)

    @Test
    fun rhythmWeekStartsMondayAndMarksToday() {
        val week = JournalAggregator.rhythmWeek(emptyList(), wednesday)
        assertEquals(7, week.size)
        assertEquals(LocalDate(2026, 8, 31), week.first().date) // Monday
        assertEquals(LocalDate(2026, 9, 6), week.last().date)   // Sunday
        assertTrue(week[2].isToday)
        assertEquals(1, week.count { it.isToday })
    }

    @Test
    fun rhythmWeekMarksLoggedDays() {
        val entries = listOf(entry("2026-08-31"), entry("2026-09-02"), entry("2026-09-02", MealType.DINNER))
        val week = JournalAggregator.rhythmWeek(entries, wednesday)
        assertTrue(week[0].logged)   // Monday
        assertTrue(week[2].logged)   // Wednesday (two meals, still one day)
        assertEquals(2, JournalAggregator.nourishingDaysCount(week))
    }

    @Test
    fun rhythmWeekOnMondayAndSunday() {
        val monday = LocalDate(2026, 8, 31)
        assertEquals(monday, JournalAggregator.rhythmWeek(emptyList(), monday).first().date)
        val sunday = LocalDate(2026, 9, 6)
        assertEquals(monday, JournalAggregator.rhythmWeek(emptyList(), sunday).first().date)
    }

    @Test
    fun snapshotCountsNutrientPresence() {
        val entries = listOf(
            entry("2026-09-01", protein = NutrientStatus.PRESENT, fibre = NutrientStatus.PRESENT),
            entry("2026-09-02", protein = NutrientStatus.PRESENT, fat = NutrientStatus.PRESENT),
            entry("2026-09-02", MealType.DINNER, protein = NutrientStatus.COULD_ADD),
        )
        val snapshot = JournalAggregator.snapshot(entries)
        assertEquals(3, snapshot.mealsLogged)
        assertEquals(2, snapshot.daysWithMeals)
        assertEquals(2, snapshot.proteinPresentCount)
        assertEquals(1, snapshot.fibrePresentCount)
        assertEquals(1, snapshot.healthyFatPresentCount)
    }

    @Test
    fun plantVarietyCountsDistinctPlants() {
        val entries = listOf(
            entry("2026-09-01", components = listOf("Spinach", "Rice", "Paneer")),
            entry("2026-09-02", components = listOf("Spinach curry", "Banana")),
        )
        // spinach, rice, banana → 3 (paneer isn't a plant keyword; spinach counted once)
        assertEquals(3, JournalAggregator.plantVariety(entries))
    }

    @Test
    fun streakCountsConsecutiveDaysEndingTodayOrYesterday() {
        val dates = listOf(
            LocalDate(2026, 9, 2), LocalDate(2026, 9, 1), LocalDate(2026, 8, 31),
            LocalDate(2026, 8, 28), // gap on 29-30
        )
        assertEquals(3, JournalAggregator.loggingStreak(dates, wednesday))
        // Nothing today, but logged yesterday → streak still counts from yesterday.
        assertEquals(2, JournalAggregator.loggingStreak(
            listOf(LocalDate(2026, 9, 1), LocalDate(2026, 8, 31)), wednesday,
        ))
        // Last log two days ago → streak broken.
        assertEquals(0, JournalAggregator.loggingStreak(listOf(LocalDate(2026, 8, 31)), wednesday))
        assertEquals(0, JournalAggregator.loggingStreak(emptyList(), wednesday))
    }
}
