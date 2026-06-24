package com.dailyroutine.app

import android.content.Context
import java.util.regex.Pattern

object CalorieSearchEngine {
    
    private const val PREFS_CACHE = "calorie_cache_pref"

    /**
     * Entry point for meal calories. 
     * Uses a local cache first, then falls back to AI Web Sync.
     */
    fun getCalories(context: Context, text: String, onResult: (Int) -> Unit) {
        val normalized = text.lowercase().trim()
        if (normalized.isEmpty()) {
            onResult(0)
            return
        }

        // 1. Check Local Cache
        val cache = context.getSharedPreferences(PREFS_CACHE, Context.MODE_PRIVATE)
        val cachedValue = cache.getInt(normalized, -1)
        if (cachedValue != -1) {
            onResult(cachedValue)
            return
        }

        // 2. AI Web Sync (Nutritionix)
        NutritionixClient.getCaloriesForMeal(normalized) { calories ->
            // Save to Cache
            if (calories > 0) {
                cache.edit().putInt(normalized, calories).apply()
            }
            onResult(calories)
        }
    }

    // This remains offline and instant
    fun calculateActiveBurn(context: Context, steps: Int, weight: Double): Double {
        return WellnessEngine.calculateActiveBurn(context, steps, weight)
    }
}
