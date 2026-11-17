package com.dailyroutine.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterIntakeSummaryTest {

    @Test
    fun adjustmentChoicesCoverAddAndRemoveFrom250MlThrough1000MlOnly() {
        assertEquals(listOf(250, 500, 750, 1000, -250, -500, -750, -1000), WaterIntakeSummary.adjustmentAmountsMl)
        assertTrue(WaterIntakeSummary.adjustmentAmountsMl.all { kotlin.math.abs(it) in 250..1000 })
        assertFalse(WaterIntakeSummary.adjustmentAmountsMl.any { kotlin.math.abs(it) > 1000 })
    }

    @Test
    fun adjustmentsChangeLitersAndDoNotAllowNegativeTotals() {
        assertEquals(1.25, WaterIntakeSummary.adjustedLiters(1.0, 250), 0.0001)
        assertEquals(0.75, WaterIntakeSummary.adjustedLiters(1.0, -250), 0.0001)
        assertEquals(0.0, WaterIntakeSummary.adjustedLiters(0.25, -1000), 0.0001)
    }

    @Test
    fun summaryMealIsNonEstimatedAndDisplaysLiters() {
        val meal = WaterIntakeSummary.createMeal("2026-10-15", 1.25)

        assertEquals("Total water intake", meal.name)
        assertEquals("1.25 L", meal.description)
        assertEquals("Hydration", meal.mealType)
        assertTrue(meal.isWaterIntakeSummary)
        assertNull(meal.calories)
        assertNull(meal.nutritionInfo)
        assertFalse(Meal().isWaterIntakeSummary)
    }

    @Test
    fun amountLabelDisplaysLegacyMilliliterEntriesAsLiters() {
        val legacyMeal = WaterIntakeSummary.createMeal("2026-10-14", 1.25)
            .copy(description = "1250 ml total water intake")

        assertEquals("1.25 L", WaterIntakeSummary.amountLabel(legacyMeal))
        assertEquals("1.25 L", WaterIntakeSummary.amountLabel(WaterIntakeSummary.createMeal("2026-10-15", 1.25)))
    }

    @Test
    fun waterSummaryAlwaysSortsAfterEveryMealForItsDate() {
        val lateMeal = Meal(name = "Late snack", hour = 23, minute = 59)
        val earlyMeal = Meal(name = "Breakfast", hour = 8)
        val summary = WaterIntakeSummary.createMeal("2026-10-15", 1.25)

        assertEquals(
            listOf(earlyMeal, lateMeal, summary),
            WaterIntakeSummary.mealsWithSummaryLast(listOf(summary, lateMeal, earlyMeal))
        )
    }

    @Test
    fun onlyWaterSummaryDisablesTheMealDeleteAction() {
        assertFalse(WaterIntakeSummary.allowsDelete(WaterIntakeSummary.createMeal("2026-10-15", 1.25)))
        assertTrue(WaterIntakeSummary.allowsDelete(Meal(name = "Lunch")))
    }

    @Test
    fun literFormattingKeepsQuarterLiterPrecisionWithoutTrailingZeros() {
        assertEquals("1.25", WaterIntakeSummary.formatLiters(1.25))
        assertEquals("1.5", WaterIntakeSummary.formatLiters(1.5))
        assertEquals("1", WaterIntakeSummary.formatLiters(1.0))
    }
}
