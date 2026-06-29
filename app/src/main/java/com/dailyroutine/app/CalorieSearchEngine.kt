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
     * Uses AI Cache -> Live AI (Gemini).
     * STRICTLY Live AI based as requested. No local fallbacks.
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

        // 2. Live AI Web Sync (Gemini)
        scope.launch {
            val calories = GeminiClient.getCaloriesForMeal(normalized)
            if (calories > 0) {
                cache.edit().putInt(normalized, calories).apply()
            }
            onResult(calories)
        }
    }

    fun calculateActiveBurn(context: Context, steps: Int, weight: Double): Double {
        return WellnessEngine.calculateActiveBurn(context, steps, weight)
    }
}
