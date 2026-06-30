package com.dailyroutine.app

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object CalorieSearchEngine {
    
    private const val PREFS_CACHE = "calorie_cache_pref"
    private val scope = CoroutineScope(Dispatchers.Main)

    /**
     * Entry point for meal calories.
     * Uses cache -> Gemini -> local Indian DB -> generic estimate.
     */
    fun getCalories(context: Context, text: String, onResult: (Int) -> Unit) {
        val normalized = text.lowercase().trim()
        if (normalized.isEmpty()) {
            onResult(0)
            return
        }

        getCaloriesForCacheKey(
            context = context,
            cacheKey = "v1|$normalized",
            onResult = onResult
        ) {
            GeminiClient.getCaloriesForMeal(normalized, context.applicationContext)
        }
    }

    fun getCalories(context: Context, title: String, description: String, onResult: (Int) -> Unit) {
        val normalizedTitle = title.lowercase().trim()
        val normalizedDescription = description.lowercase().trim()
        if (normalizedTitle.isEmpty() && normalizedDescription.isEmpty()) {
            onResult(0)
            return
        }

        getCaloriesForCacheKey(
            context = context,
            cacheKey = "v2|title=$normalizedTitle|desc=$normalizedDescription",
            onResult = onResult
        ) {
            GeminiClient.getCaloriesForMeal(
                title = normalizedTitle,
                description = normalizedDescription,
                context = context.applicationContext
            )
        }
    }

    private fun getCaloriesForCacheKey(
        context: Context,
        cacheKey: String,
        onResult: (Int) -> Unit,
        calculate: suspend () -> Int
    ) {
        val cache = context.getSharedPreferences(PREFS_CACHE, Context.MODE_PRIVATE)
        val cachedValue = cache.getInt(cacheKey, -1)
        if (cachedValue != -1) {
            onResult(cachedValue)
            return
        }

        // Gemini is primary. Local Indian DB is used only if Gemini fails.
        scope.launch {
            val calories = calculate()
            if (calories > 0) {
                cache.edit().putInt(cacheKey, calories).apply()
            }
            onResult(calories)
        }
    }

    fun calculateActiveBurn(context: Context, steps: Int, weight: Double): Double {
        return WellnessEngine.calculateActiveBurn(context, steps, weight)
    }
}
