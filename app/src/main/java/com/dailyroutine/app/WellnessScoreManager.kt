package com.dailyroutine.app

import android.content.Context

object WellnessScoreManager {

    fun calculateDailyScore(
        context: Context,
        steps: Int,
        sleepHours: Double,
        doneHabits: Int,
        totalHabits: Int
    ): Int {
        val healthDataManager = HealthDataManager(context)
        val stepGoal = healthDataManager.getDailyStepGoal()
        val sleepGoal = 8.0 // Fixed 8-hour target

        // 1. Steps Component (20% Weight)
        val stepProgress = if (stepGoal > 0) (steps.toDouble() / stepGoal) else 1.0
        val stepScore = (stepProgress * 20.0).coerceAtMost(20.0)

        // 2. Sleep Component (40% Weight)
        val sleepProgress = (sleepHours / sleepGoal)
        val sleepScore = (sleepProgress * 40.0).coerceAtMost(40.0)

        // 3. Habits Component (40% Weight)
        val habitProgress = if (totalHabits > 0) (doneHabits.toDouble() / totalHabits) else 1.0
        val habitScore = (habitProgress * 40.0).coerceAtMost(40.0)

        return (stepScore + sleepScore + habitScore).toInt().coerceIn(0, 100)
    }
}
