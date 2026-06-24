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

    // 🧪 Scientific MET Table (Metabolic Equivalent of Task)
    private val MET_TABLE = mapOf(
        "walking_slow" to 2.0, "walking_avg" to 3.5, "walking_brisk" to 5.0,
        "running" to 10.0, "pushups" to 3.8, "squats" to 5.0, "yoga" to 2.5,
        "weightlifting_light" to 3.0, "weightlifting_heavy" to 6.0, "hiit" to 8.0,
        "cycling" to 7.5, "swimming" to 7.0, "plank" to 2.8, "jumping_jacks" to 8.0
    )

    fun calculateActiveBurn(context: Context, steps: Int, weight: Double): Double {
        val userWeight = if (weight > 0) weight else 70.0
        val hdm = HealthDataManager(context)

        // 1. 🚶 Scientific Walking Burn (MET-Based)
        val distanceKm = hdm.getDistanceKm()
        val moveMins = hdm.getMoveMinutes()
        
        val metWalking = when {
            moveMins <= 0 -> 0.0
            (distanceKm / (moveMins / 60.0)) > 6.0 -> MET_TABLE["walking_brisk"]!!
            (distanceKm / (moveMins / 60.0)) > 4.0 -> MET_TABLE["walking_avg"]!!
            else -> MET_TABLE["walking_slow"]!!
        }
        
        val walkingBurn = (metWalking * 3.5 * userWeight / 200.0) * moveMins
        
        // 2. 💪 Scientific Workout Burn (MET + Intensity)
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val doneIds = RoutineProgressStore.getDoneIds(context)
        val doneExercises = PlanManager(context).getExercisesForDate(todayStr).filter { it.id.toString() in doneIds }
        
        var workoutBurn = 0.0
        doneExercises.forEach { ex ->
            val totalReps = try {
                val r = ex.reps.split("-").first().filter { it.isDigit() }.toIntOrNull() ?: 10
                ex.sets * r
            } catch(e: Exception) { ex.sets * 10 }
            
            val durationMins = (totalReps * 4) / 60.0
            val baseMET = when {
                ex.name.lowercase().contains("pushup") -> MET_TABLE["pushups"]!!
                ex.name.lowercase().contains("squat") -> MET_TABLE["squats"]!!
                ex.name.lowercase().contains("yoga") -> MET_TABLE["yoga"]!!
                ex.intensity > 80 -> MET_TABLE["weightlifting_heavy"]!!
                else -> MET_TABLE["weightlifting_light"]!!
            }
            
            val adjustedMET = baseMET * (0.8 + (ex.intensity / 100.0) * 0.4)
            workoutBurn += (adjustedMET * 3.5 * userWeight / 200.0) * durationMins
        }
        
        return walkingBurn + workoutBurn
    }

    fun calculateIntakeForDate(context: Context, date: String, onResult: (Int) -> Unit) {
        val meals = PlanManager(context).getMealsForDate(date)
        if (meals.isEmpty()) {
            onResult(0)
            return
        }

        var total = 0
        var processedCount = 0
        meals.forEach { meal ->
            CalorieSearchEngine.getCalories(context, "${meal.name} ${meal.description}") { cals ->
                total += cals
                processedCount++
                if (processedCount == meals.size) {
                    onResult(total)
                }
            }
        }
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
        val milestoneCal = Calendar.getInstance()

        var totalSteps = 0L
        for (i in 0 until 60) {
            totalSteps += hdm.getHistoricalSteps(sdf.format(milestoneCal.time))
            milestoneCal.add(Calendar.DAY_OF_YEAR, -1)
        }
        if (totalSteps >= 100_000) UserPreferencesStore.unlockBadge(context, "century_walker")

        milestoneCal.time = Date()
        var waterStreak = 0
        for (i in 0 until 7) {
            if (hdm.getWaterIntake(sdf.format(milestoneCal.time)) >= 2.0) waterStreak++ else break
            milestoneCal.add(Calendar.DAY_OF_YEAR, -1)
        }
        if (waterStreak >= 7) UserPreferencesStore.unlockBadge(context, "hydration_hero")
    }
}
