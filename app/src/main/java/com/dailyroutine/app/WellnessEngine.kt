package com.dailyroutine.app

import android.content.Context
import java.text.SimpleDateFormat
import java.util.*

object WellnessEngine {

    fun calculateBMR(context: Context): Double {
        val weight = HealthDataManager(context).getWeight(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
            .let { if (it > 0) it else 70.0 }
        val height = UserPreferencesStore.getUserHeight(context)
        val age = UserPreferencesStore.getUserAge(context)
        val gender = UserPreferencesStore.getUserGender(context)

        return if (gender == "Male") {
            (10 * weight) + (6.25 * height) - (5 * age) + 5
        } else {
            (10 * weight) + (6.25 * height) - (5 * age) - 161
        }
    }

    fun calculateActiveBurn(context: Context, steps: Int, weight: Double): Double {
        val userWeight = if (weight > 0) weight else 70.0
        val walkingBurn = steps * userWeight * 0.00055
        
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val doneIds = RoutineProgressStore.getDoneIds(context)
        val doneExercises = PlanManager(context).getExercisesForDate(todayStr).filter { it.id.toString() in doneIds }
        val workoutBurn = doneExercises.sumOf { (it.intensity * 2.5) }
        
        return walkingBurn + workoutBurn
    }

    fun calculateIntake(context: Context): Int {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val meals = PlanManager(context).getMealsForDate(todayStr)
        return meals.sumOf { CalorieSearchEngine.getCalories("${it.name} ${it.description}") }
    }

    fun getMasterCalorieBalance(context: Context, steps: Int): Int {
        val intake = calculateIntake(context)
        val bmr = calculateBMR(context)
        val weight = HealthDataManager(context).getWeight(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
        val activeBurn = calculateActiveBurn(context, steps, weight)
        
        // Net Balance = Intake - (BMR + Active Burn)
        // However, usually "Net" in trackers means Intake - Active Burn relative to goal
        // We will show Intake - Active Burn as the primary metric
        return intake - activeBurn.toInt()
    }

    fun getTrendInsight(context: Context): String {
        val hdm = HealthDataManager(context)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        
        var currentWeekSteps = 0L
        for (i in 0 until 7) {
            currentWeekSteps += hdm.getHistoricalSteps(sdf.format(cal.time))
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        
        var lastWeekSteps = 0L
        for (i in 0 until 7) {
            lastWeekSteps += hdm.getHistoricalSteps(sdf.format(cal.time))
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        
        if (lastWeekSteps == 0L) return "Keep moving to start seeing your weekly trends! 🚶"
        
        val diff = ((currentWeekSteps - lastWeekSteps).toDouble() / lastWeekSteps) * 100
        return when {
            diff > 10 -> "You're crushing it! You walked ${diff.toInt()}% more this week. 🏆"
            diff < -10 -> "A bit slower this week. Let's aim to beat last week's total! 💪"
            else -> "Steady progress! You're maintaining a consistent pace. ✨"
        }
    }

    data class Milestone(val id: String, val title: String, val emoji: String, val description: String)

    val milestones = listOf(
        Milestone("century_walker", "Century Walker", "🚶", "100k total steps achieved!"),
        Milestone("hydration_hero", "Hydration Hero", "💧", "7-day water streak!"),
        Milestone("sleep_master", "Sleep Master", "😴", "Perfect sleep for 30 days!"),
        Milestone("early_bird", "Early Bird", "🌅", "5 consecutive 6 AM check-ins!")
    )

    fun checkMilestones(context: Context) {
        val hdm = HealthDataManager(context)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()

        // 1. Century Walker (Total Steps)
        var totalSteps = 0L
        for (i in 0 until 60) {
            totalSteps += hdm.getHistoricalSteps(sdf.format(cal.time))
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        if (totalSteps >= 100_000) UserPreferencesStore.unlockBadge(context, "century_walker")

        // 2. Hydration Hero (7-day streak)
        cal.time = Date()
        var waterStreak = 0
        for (i in 0 until 7) {
            if (hdm.getWaterIntake(sdf.format(cal.time)) >= 2.0) waterStreak++ else break
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        if (waterStreak >= 7) UserPreferencesStore.unlockBadge(context, "hydration_hero")
    }
}
