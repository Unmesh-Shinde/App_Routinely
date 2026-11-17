package com.dailyroutine.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiClientNutritionParsingTest {

    @Test
    fun parseMealNutritionResponse_acceptsLowCaloriesWhenMacrosAreOmitted() {
        val nutrition = GeminiClient.parseMealNutritionResponse(
            JSONObject("""{"total_calories":3}""")
        )

        assertEquals(3, nutrition.calories)
        assertNull(nutrition.carbsG)
        assertNull(nutrition.proteinG)
        assertNull(nutrition.fatG)
        assertTrue(nutrition.vitamins.isEmpty())
        assertTrue(nutrition.minerals.isEmpty())
    }

    @Test
    fun parseMealNutritionResponse_preservesExplicitZeroMacrosAndPartialValues() {
        val nutrition = GeminiClient.parseMealNutritionResponse(
            JSONObject("""{"calories":3,"carbs_g":0.9,"protein_g":0,"fat_g":0}""")
        )

        assertEquals(3, nutrition.calories)
        assertEquals(0.9, nutrition.carbsG!!, 0.001)
        assertEquals(0.0, nutrition.proteinG!!, 0.001)
        assertEquals(0.0, nutrition.fatG!!, 0.001)
        assertNull(nutrition.fiberG)
    }

    @Test
    fun parseMealNutritionResponse_doesNotTreatInvalidOrNegativeNutrientsAsZero() {
        val nutrition = GeminiClient.parseMealNutritionResponse(
            JSONObject("""{"total_calories":-1,"carbs_g":"unknown","protein_g":-2}""")
        )

        assertNull(nutrition.calories)
        assertNull(nutrition.carbsG)
        assertNull(nutrition.proteinG)
    }

    @Test
    fun displayCalories_preservesExplicitZeroAndLeavesMissingEstimateUnknown() {
        assertEquals(0, Meal(nutritionInfo = MealNutritionInfo(calories = 0)).displayCalories)
        assertEquals(3, Meal(calories = 3, nutritionInfo = MealNutritionInfo(calories = null)).displayCalories)
        assertNull(Meal(nutritionInfo = MealNutritionInfo()).displayCalories)
    }
}
