package com.dailyroutine.app

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

object WorkoutMetSearchEngine {

    private const val PREFS_CACHE = "workout_met_cache_pref"
    private val scope = CoroutineScope(Dispatchers.Main)

    fun enrichIfNeeded(context: Context, exercise: Exercise, onResult: (Exercise) -> Unit) {
        if (!WellnessEngine.shouldEnrichWorkoutMet(exercise)) {
            onResult(exercise.copy(
                estimatedMet = WellnessEngine.getLocalWorkoutMet(exercise),
                metSource = "local"
            ))
            return
        }

        val cache = context.getSharedPreferences(PREFS_CACHE, Context.MODE_PRIVATE)
        val cacheKey = cacheKeyFor(exercise)
        val cachedMet = cache.getFloat(cacheKey, -1f)
        if (cachedMet > 0f) {
            onResult(exercise.copy(estimatedMet = cachedMet.toDouble(), metSource = "gemini_cache"))
            return
        }

        scope.launch {
            val geminiMet = GeminiClient.getMetForWorkout(exercise)
            val enriched = if (geminiMet in 1.0..15.0) {
                cache.edit().putFloat(cacheKey, geminiMet.toFloat()).apply()
                exercise.copy(estimatedMet = geminiMet, metSource = "gemini")
            } else {
                exercise.copy(
                    estimatedMet = WellnessEngine.getLocalWorkoutMet(exercise),
                    metSource = "local_fallback"
                )
            }
            onResult(enriched)
        }
    }

    private fun cacheKeyFor(exercise: Exercise): String {
        val normalizedName = exercise.name.lowercase(Locale.US).trim()
        val normalizedTarget = exercise.targetArea.lowercase(Locale.US).trim()
        return "v1|name=$normalizedName|target=$normalizedTarget"
    }
}
