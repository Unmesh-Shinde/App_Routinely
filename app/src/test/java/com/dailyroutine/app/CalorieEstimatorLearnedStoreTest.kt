package com.dailyroutine.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CalorieEstimatorLearnedStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("learned_meal_nutrition_store", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("plans_pref", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun saveLearnedMeal_persistsAndRetrievesFullNutritionInfo() {
        val nutrition = MealNutritionInfo(
            calories = 620,
            carbsG = 75.0,
            proteinG = 32.0,
            fatG = 18.0,
            fiberG = 8.5,
            vitamins = mapOf("Vitamin C" to "45 mg", "Vitamin D" to "2.5 mcg"),
            minerals = mapOf("Iron" to "4.2 mg", "Calcium" to "210 mg"),
            aminoAcids = mapOf("Leucine" to "2.1 g", "Lysine" to "1.8 g"),
            otherNutrients = mapOf("Omega-3" to "0.4 g")
        )

        CalorieEstimator.saveLearnedMeal(context, "Avocado Protein Bowl", "With poached egg and sourdough", nutrition)

        val retrieved = CalorieEstimator.getLearnedMealNutrition(context, "Avocado Protein Bowl", "With poached egg and sourdough")
        assertNotNull(retrieved)
        assertEquals(620, retrieved?.calories)
        assertEquals(75.0, retrieved?.carbsG ?: 0.0, 0.01)
        assertEquals(32.0, retrieved?.proteinG ?: 0.0, 0.01)
        assertEquals("45 mg", retrieved?.vitamins?.get("Vitamin C"))
        assertEquals("4.2 mg", retrieved?.minerals?.get("Iron"))
        assertEquals("2.1 g", retrieved?.aminoAcids?.get("Leucine"))
        assertEquals("0.4 g", retrieved?.otherNutrients?.get("Omega-3"))
    }

    @Test
    fun getLearnedMealNutrition_matchesSimilarMealsWith95PercentSimilarity() {
        val nutrition = MealNutritionInfo(
            calories = 780,
            carbsG = 90.0,
            proteinG = 40.0,
            fatG = 22.0,
            fiberG = 10.0,
            vitamins = mapOf("Vitamin A" to "300 mcg"),
            minerals = mapOf("Potassium" to "550 mg")
        )

        CalorieEstimator.saveLearnedMeal(context, "Custom Smoothie Bowl", "Mixed berries and chia seeds", nutrition)

        // 95%+ similar query
        val estimatedNutrition = CalorieEstimator.getLearnedMealNutrition(context, "Custom Smoothie Bowls", "Mixed berries and chia seeds")
        assertNotNull(estimatedNutrition)
        assertEquals(780, estimatedNutrition?.calories)
        assertEquals(90.0, estimatedNutrition?.carbsG ?: 0.0, 0.01)

        // Distinct meal (< 95% similarity) should return null
        val distinctMatch = CalorieEstimator.getLearnedMealNutrition(context, "Grilled Salmon Steak", "With steamed asparagus")
        assertNull(distinctMatch)
    }

    @Test
    fun planManager_persistsMealWithNutritionInfo() {
        val manager = PlanManager(context)
        val nutrition = MealNutritionInfo(
            calories = 450,
            carbsG = 50.0,
            proteinG = 25.0,
            fatG = 12.0,
            fiberG = 6.0,
            vitamins = mapOf("Vitamin B12" to "1.2 mcg")
        )
        val meal = Meal(
            id = 501,
            name = "Grilled Chicken Salad",
            description = "Light olive oil dressing",
            calories = 450,
            nutritionInfo = nutrition
        )

        manager.saveMealForDate("2026-08-01", meal)

        val savedMeals = manager.getMealsForDate("2026-08-01")
        assertEquals(1, savedMeals.size)
        val savedMeal = savedMeals.single()
        assertEquals("Grilled Chicken Salad", savedMeal.name)
        assertEquals(450, savedMeal.calories)
        assertNotNull(savedMeal.nutritionInfo)
        assertEquals(50.0, savedMeal.nutritionInfo?.carbsG ?: 0.0, 0.01)
        assertEquals("1.2 mcg", savedMeal.nutritionInfo?.vitamins?.get("Vitamin B12"))
    }

    @Test
    fun getLearnedMealNutrition_purgesAndIgnoresStaleZeroMacroCacheEntries() {
        // Save corrupted/stale 0-macro entry
        val staleEntry = MealNutritionInfo(calories = 500, carbsG = 0.0, proteinG = 0.0, fatG = 0.0)
        CalorieEstimator.saveLearnedMeal(context, "Stale Bowl", "No macros", staleEntry)

        // Retrieval should return null and purge the corrupted entry
        val retrieved = CalorieEstimator.getLearnedMealNutrition(context, "Stale Bowl", "No macros")
        assertNull(retrieved)
    }

    @Test
    fun getLearnedMealNutrition_ignoresLegacyEntriesFromBeforeNutritionCacheV2() {
        val staleNutrition = MealNutritionInfo(
            calories = 128,
            carbsG = 16.0,
            proteinG = 7.0,
            fatG = 4.0,
            fiberG = 1.9,
            vitamins = mapOf("Vitamin D" to "1.5 mcg", "Vitamin B12" to "0.6 mcg", "Vitamin B6" to "0.4 mg"),
            minerals = mapOf("Zinc" to "1.8 mg", "Magnesium" to "45 mg"),
            otherNutrients = mapOf("Omega-3" to "0.2 g")
        )
        context.getSharedPreferences("learned_meal_nutrition_store", Context.MODE_PRIVATE)
            .edit()
            .putString("learned|t=legacy bowl|d=stale saved details", Gson().toJson(staleNutrition))
            .commit()

        assertNull(CalorieEstimator.getLearnedMealNutrition(context, "Legacy Bowl", "Stale Saved Details"))
    }
}
