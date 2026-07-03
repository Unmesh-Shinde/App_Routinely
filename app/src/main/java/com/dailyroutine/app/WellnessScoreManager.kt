package com.dailyroutine.app

import android.content.Context

object WellnessScoreManager {

    private const val PREFS_NAME = "wellness_score_history_pref"
    private const val KEY_SCORE_PREFIX = "score_"

    fun calculateDailyScore(
        context: Context,
        steps: Int,
        sleepHours: Double,
        workoutDone: Int,
        workoutTotal: Int,
        nutritionDone: Int,
        nutritionTotal: Int
    ): Int {
        val healthDataManager = HealthDataManager(context)
        val stepGoal = healthDataManager.getDailyStepGoal()
        val sleepGoal = 8.0 // Fixed 8-hour target

        // Fetch User's weightage preferences
        val prefSleepWeight = UserPreferencesStore.getSleepWeight(context).toDouble()
        val prefWorkoutWeight = UserPreferencesStore.getWorkoutWeight(context).toDouble()
        val prefNutritionWeight = UserPreferencesStore.getNutritionWeight(context).toDouble()
        val prefStepsWeight = UserPreferencesStore.getStepsWeight(context).toDouble()

        val activeFactors = mutableListOf<Triple<Double, Double, Double>>() // (ActualScoreContribution, MaxPossibleScore, Weight)

        // 1. Sleep Component: Check if logged
        // If sleep is 0.0, we assume not logged (since 0.0 sleep is extremely unlikely for a living human)
        if (sleepHours > 0.0) {
            val progress = (sleepHours / sleepGoal).coerceAtMost(1.0)
            activeFactors.add(Triple(progress * prefSleepWeight, prefSleepWeight, prefSleepWeight))
        }

        // 2. Workout Component: Check if items are planned
        if (workoutTotal > 0) {
            val progress = (workoutDone.toDouble() / workoutTotal).coerceAtMost(1.0)
            activeFactors.add(Triple(progress * prefWorkoutWeight, prefWorkoutWeight, prefWorkoutWeight))
        }

        // 3. Nutrition Component: Check if items are planned
        if (nutritionTotal > 0) {
            val progress = (nutritionDone.toDouble() / nutritionTotal).coerceAtMost(1.0)
            activeFactors.add(Triple(progress * prefNutritionWeight, prefNutritionWeight, prefNutritionWeight))
        }

        // 4. Steps Component: Check if steps are > 0 (logged/tracked)
        if (steps > 0) {
            val progress = (steps.toDouble() / stepGoal).coerceAtMost(1.0)
            activeFactors.add(Triple(progress * prefStepsWeight, prefStepsWeight, prefStepsWeight))
        }

        // If no data is logged at all, return 0
        if (activeFactors.isEmpty()) return 0

        val totalActual = activeFactors.sumOf { it.first }
        val totalPossibleWeight = activeFactors.sumOf { it.third }

        // Rescale to 100%
        val finalScore = (totalActual / totalPossibleWeight) * 100.0

        return finalScore.toInt().coerceIn(0, 100)
    }

    fun saveDailyScore(context: Context, date: String, score: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_SCORE_PREFIX + date, score.coerceIn(0, 100))
            .apply()
    }

    fun getSavedDailyScore(context: Context, date: String): Int? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = KEY_SCORE_PREFIX + date
        return if (prefs.contains(key)) prefs.getInt(key, 0).coerceIn(0, 100) else null
    }
}
