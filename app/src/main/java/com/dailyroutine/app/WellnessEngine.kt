package com.dailyroutine.app

import android.content.Context
import java.text.SimpleDateFormat
import java.util.*

object WellnessEngine {

    fun calculateBMR(context: Context): Double {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val weight = HealthDataManager(context).getWeight(todayStr)
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
        val hdm = HealthDataManager(context)

        // 1. Walking/Distance Burn (Scientific: approx 0.72 kcal per km per kg)
        // Using real distance from Health Connect
        val distanceKm = hdm.getDistanceKm()
        val walkingBurn = distanceKm * userWeight * 0.72
        
        // 2. Workout Burn (Type, Reps, Intensity)
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val doneIds = RoutineProgressStore.getDoneIds(context)
        val doneExercises = PlanManager(context).getExercisesForDate(todayStr).filter { it.id.toString() in doneIds }
        
        var workoutBurn = 0.0
        doneExercises.forEach { ex ->
            val totalReps = try {
                val r = ex.reps.split("-").first().filter { it.isDigit() }.toIntOrNull() ?: 10
                ex.sets * r
            } catch(e: Exception) { ex.sets * 10 }
            
            // Formula: Reps * (Base + Intensity Scalar) * Weight Ratio
            // Base 0.1 kcal/rep, Max 0.5 kcal/rep for intensity 100
            val intensityScalar = (ex.intensity / 100.0) * 0.4
            val burnPerRep = (0.1 + intensityScalar) * (userWeight / 70.0)
            workoutBurn += (totalReps * burnPerRep)
        }
        
        // 3. Heart Points (Active Minutes) - Bonus for sustained metabolic elevation
        val moveMins = hdm.getMoveMinutes()
        val intensityBonus = moveMins * 0.8 * (userWeight / 70.0) // 0.8 kcal per active min

        return walkingBurn + workoutBurn + intensityBonus
    }

    fun calculateIntake(context: Context): Int {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val meals = PlanManager(context).getMealsForDate(todayStr)
        return meals.sumOf { CalorieSearchEngine.getCalories("${it.name} ${it.description}") }
    }

    fun getMasterCalorieBalance(context: Context, steps: Int): Int {
        val intake = calculateIntake(context)
        val weight = HealthDataManager(context).getWeight(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
        val activeBurn = calculateActiveBurn(context, steps, weight)
        
        // Current dashboard logic: Net = Intake - Active Burn
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

        // 1. Century Walker
        var totalSteps = 0L
        val milestoneCal = Calendar.getInstance()
        for (i in 0 until 60) {
            totalSteps += hdm.getHistoricalSteps(sdf.format(milestoneCal.time))
            milestoneCal.add(Calendar.DAY_OF_YEAR, -1)
        }
        if (totalSteps >= 100_000) UserPreferencesStore.unlockBadge(context, "century_walker")

        // 2. Hydration Hero
        milestoneCal.time = Date()
        var waterStreak = 0
        for (i in 0 until 7) {
            if (hdm.getWaterIntake(sdf.format(milestoneCal.time)) >= 2.0) waterStreak++ else break
            milestoneCal.add(Calendar.DAY_OF_YEAR, -1)
        }
        if (waterStreak >= 7) UserPreferencesStore.unlockBadge(context, "hydration_hero")
    }
}
