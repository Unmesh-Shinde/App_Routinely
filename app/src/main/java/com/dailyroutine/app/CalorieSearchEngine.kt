package com.dailyroutine.app

import android.content.Context

object CalorieSearchEngine {
    
    private const val PREFS_CACHE = "calorie_cache_pref"

    /**
     * Entry point for meal calories. 
     * Strictly uses Local Cache (Already learned from AI) or Live AI Web Sync.
     */
    fun getCalories(context: Context, text: String, onResult: (Int) -> Unit) {
        val normalized = text.lowercase().trim()
        if (normalized.isEmpty()) {
            onResult(0)
            return
        }

        // 1. Check Local Cache (Already learned from AI)
        val cache = context.getSharedPreferences(PREFS_CACHE, Context.MODE_PRIVATE)
        val cachedValue = cache.getInt(normalized, -1)
        if (cachedValue != -1) {
            onResult(cachedValue)
            return
        }

        // 2. Strict AI Web Sync (Nutritionix)
        NutritionixClient.getCaloriesForMeal(normalized) { calories ->
            if (calories > 0) {
                // Learn and Cache for next time
                cache.edit().putInt(normalized, calories).apply()
            }
            // If API fails or returns 0, we return 0 (no fallback)
            onResult(calories)
        }
    }

    fun calculateActiveBurn(context: Context, steps: Int, weight: Double): Double {
        return WellnessEngine.calculateActiveBurn(context, steps, weight)
    }
}
