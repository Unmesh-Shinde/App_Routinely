package com.dailyroutine.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalorieEstimatorTest {

    @Test
    fun normalize_cleansAndTrimsText() {
        assertEquals("chicken salad with olive oil", CalorieEstimator.normalize("  Chicken Salad, with Olive Oil!! "))
    }

    @Test
    fun calculateSimilarity_exactMatchesReturnOne() {
        assertEquals(1.0, CalorieEstimator.calculateSimilarity("Oatmeal with Blueberries", "oatmeal with blueberries"), 0.001)
    }

    @Test
    fun calculateSimilarity_closeMatchesMeet95PercentThreshold() {
        val score = CalorieEstimator.calculateSimilarity("Grilled Chicken Salad", "Grilled Chicken Salads")
        assertTrue("Expected similarity >= 0.95, got $score", score >= 0.95)
    }

    @Test
    fun calculateSimilarity_differentMealsReturnBelow95PercentThreshold() {
        val score = CalorieEstimator.calculateSimilarity("Grilled Chicken Salad", "Salmon Avocado Toast")
        assertTrue("Expected similarity < 0.95, got $score", score < 0.95)
    }

    @Test
    fun enrichMealNutrition_preservesGeminiValuesWithoutSynthesizingBreakdowns() {
        val baseInfo = MealNutritionInfo(
            calories = 500,
            carbsG = 55.0,
            proteinG = 30.0,
            fatG = 15.0,
            fiberG = 6.0
        )
        val info = CalorieEstimator.enrichMealNutrition(baseInfo)

        assertEquals(500, info.calories ?: -1)
        assertEquals(55.0, info.carbsG ?: 0.0, 0.01)
        assertEquals(30.0, info.proteinG ?: 0.0, 0.01)
        assertTrue(info.carbBreakdown.isEmpty())
        assertTrue(info.fatBreakdown.isEmpty())
        assertTrue(info.aminoAcids.isEmpty())
        assertTrue(info.antioxidants.isEmpty())
        assertTrue(info.otherNutrients.isEmpty())
    }

    @Test
    fun enrichMealNutrition_doesNotInventMicronutrientOrOmega3Values() {
        val info = CalorieEstimator.enrichMealNutrition(
            MealNutritionInfo(calories = 128, carbsG = 16.0, proteinG = 7.0, fatG = 4.0, fiberG = 1.9)
        )

        assertTrue(info.vitamins.isEmpty())
        assertTrue(info.minerals.isEmpty())
        assertTrue("Omega-3" !in info.fatBreakdown)
        assertTrue("Omega-3" !in info.otherNutrients)
    }

    @Test
    fun hasAnyNutrition_acceptsPartialNutritionAndExplicitZeros() {
        val incomplete = MealNutritionInfo(
            calories = 128,
            vitamins = mapOf("Vitamin D" to "1.5 mcg"),
            minerals = mapOf("Magnesium" to "45 mg")
        )

        assertTrue(incomplete.hasAnyNutrition())
        assertTrue(MealNutritionInfo(calories = 3, carbsG = 0.9, proteinG = 0.0, fatG = 0.0).hasAnyNutrition())
        assertTrue(MealNutritionInfo(calories = 0, carbsG = 0.0, proteinG = 0.0, fatG = 0.0).hasAnyNutrition())
        assertFalse(MealNutritionInfo().hasAnyNutrition())
    }
}
