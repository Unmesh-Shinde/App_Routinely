package com.dailyroutine.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InFlightRequestRegistryTest {

    @Test
    fun concurrentCallersForSameKeyJoinOneRequestAndReceiveItsResult() {
        val registry = InFlightRequestRegistry<String, String>()
        val results = mutableListOf<String>()

        assertTrue(registry.addOrJoin("meal") { results.add(it) })
        assertFalse(registry.addOrJoin("meal") { results.add(it) })
        registry.complete("meal", "nutrition")

        assertEquals(listOf("nutrition", "nutrition"), results)
    }

    @Test
    fun failedRequestIsRemovedSoTheSameKeyCanBeRetried() {
        val registry = InFlightRequestRegistry<String, NutritionResult>()
        val results = mutableListOf<NutritionResult>()

        assertTrue(registry.addOrJoin("meal") { results.add(it) })
        registry.complete("meal", NutritionResult.Error("unavailable", "Try again"))

        assertTrue("A failed request must not block retry", registry.addOrJoin("meal") { results.add(it) })
        registry.complete("meal", NutritionResult.Success(MealNutritionInfo(calories = 250, carbsG = 30.0, proteinG = 15.0, fatG = 8.0)))

        assertEquals(2, results.size)
        assertTrue(results[0] is NutritionResult.Error)
        assertTrue(results[1] is NutritionResult.Success)
    }
}
