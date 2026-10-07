package com.dailyroutine.app

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

sealed class NutritionResult {
    data class Success(val info: MealNutritionInfo, val fromCache: Boolean = false) : NutritionResult()
    data class Error(val code: String, val message: String) : NutritionResult()
}

object CalorieSearchEngine {
    
    // New store and key namespace intentionally invalidate entries with synthesized nutrient values.
    private const val PREFS_CACHE = "calorie_cache_pref_v6"
    private val scope = CoroutineScope(Dispatchers.Main)

    fun clearCache(context: Context) {
        context.getSharedPreferences(PREFS_CACHE, Context.MODE_PRIVATE).edit().clear().apply()
        CalorieEstimator.clearSmartCache(context)
    }

    fun getCalories(context: Context, text: String, onResult: (Int) -> Unit) {
        getMealNutrition(context, text, "") { info ->
            onResult(info.calories)
        }
    }

    fun getCalories(context: Context, title: String, description: String, onResult: (Int) -> Unit) {
        getMealNutrition(context, title, description) { info ->
            onResult(info.calories)
        }
    }

    fun getMealNutrition(context: Context, title: String, description: String, onResult: (MealNutritionInfo) -> Unit) {
        getMealNutritionResult(context, title, description) { result ->
            when (result) {
                is NutritionResult.Success -> onResult(result.info)
                is NutritionResult.Error -> onResult(MealNutritionInfo())
            }
        }
    }

    fun getMealNutritionResult(
        context: Context,
        title: String,
        description: String,
        onResult: (NutritionResult) -> Unit
    ) {
        val normalizedTitle = title.lowercase().trim()
        val normalizedDescription = description.lowercase().trim()
        if (normalizedTitle.isEmpty() && normalizedDescription.isEmpty()) {
            onResult(NutritionResult.Success(MealNutritionInfo(), fromCache = true))
            return
        }

        val learnedLocally = CalorieEstimator.getLearnedMealNutrition(context, title, description)
        if (learnedLocally != null && learnedLocally.hasDetailedNutrition()) {
            onResult(NutritionResult.Success(learnedLocally, fromCache = true))
            return
        }

        val cacheKey = "v6_nutrition|title=$normalizedTitle|desc=$normalizedDescription"
        val cache = context.getSharedPreferences(PREFS_CACHE, Context.MODE_PRIVATE)
        val cachedJson = cache.getString(cacheKey, null)
        if (cachedJson != null) {
            try {
                var cachedObj = Gson().fromJson(cachedJson, MealNutritionInfo::class.java)
                if (cachedObj != null && cachedObj.hasDetailedNutrition()) {
                    cachedObj = CalorieEstimator.enrichMealNutrition(cachedObj)
                    cache.edit().putString(cacheKey, Gson().toJson(cachedObj)).apply()
                    CalorieEstimator.saveLearnedMeal(context, title, description, cachedObj)
                    onResult(NutritionResult.Success(cachedObj, fromCache = true))
                    return
                } else {
                    cache.edit().remove(cacheKey).apply()
                }
            } catch (_: Exception) {}
        }

        scope.launch {
            val result = GeminiClient.getNutritionResultForMeal(
                title = normalizedTitle,
                description = normalizedDescription,
                context = context.applicationContext
            )
            when (result) {
                is GeminiResult.Success -> {
                    var info = result.data
                    info = CalorieEstimator.enrichMealNutrition(info)
                    if (info.hasDetailedNutrition()) {
                        val jsonStr = Gson().toJson(info)
                        cache.edit().putString(cacheKey, jsonStr).apply()
                        CalorieEstimator.saveLearnedMeal(context, title, description, info)
                        onResult(NutritionResult.Success(info, fromCache = false))
                    } else {
                        onResult(NutritionResult.Error("ai_empty_response", "AI returned incomplete nutritional details. Please try again."))
                    }
                }
                is GeminiResult.Error -> {
                    onResult(NutritionResult.Error(result.code, result.message))
                }
            }
        }
    }

    fun calculateActiveBurn(context: Context, steps: Int, weight: Double): Double {
        return WellnessEngine.calculateActiveBurn(context, steps, weight)
    }
}
