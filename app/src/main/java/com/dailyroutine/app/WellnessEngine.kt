package com.dailyroutine.app

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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

    private val MET_TABLE = mapOf(
        "walking_slow" to 2.0,
        "walking_avg" to 3.5,
        "walking_brisk" to 5.0,
        "running" to 9.8,
        "pushups" to 3.8,
        "squats" to 5.0,
        "lunges" to 4.5,
        "yoga" to 2.5,
        "pilates" to 3.0,
        "stretching" to 2.3,
        "weightlifting_light" to 3.0,
        "weightlifting_heavy" to 6.0,
        "hiit" to 8.0,
        "cardio" to 7.0,
        "cycling" to 7.5,
        "swimming" to 7.0,
        "plank" to 2.8,
        "crunches" to 3.8,
        "burpees" to 8.0,
        "jumping_jacks" to 8.0,
        "skipping" to 10.0,
        "dance" to 5.5,
        "stairs" to 8.8
    )

    fun calculateActiveBurn(context: Context, steps: Int, weight: Double): Double {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return calculateActiveBurnForDate(context, todayStr, steps, weight)
    }

    fun calculateActiveBurnForDate(context: Context, date: String, steps: Int, weight: Double): Double {
        val userWeight = if (weight > 0) weight else 70.0
        val hdm = HealthDataManager(context)

        val walkingBurn = calculateWalkingBurn(hdm, steps, userWeight)
        val workoutBurn = calculateWorkoutBurn(context, date, userWeight)

        return walkingBurn + workoutBurn
    }

    fun calculateIntakeForDate(context: Context, date: String, onResult: (Int) -> Unit) {
        val meals = PlanManager(context).getMealsForDate(date)
        if (meals.isEmpty()) {
            onResult(0)
            return
        }

        val totalIntake = meals.sumOf { it.calories }
        Log.d("WellnessEngine", "Intake for $date: $totalIntake kcal")
        onResult(totalIntake)
    }

    private fun calculateWalkingBurn(hdm: HealthDataManager, steps: Int, weightKg: Double): Double {
        val distanceKm = hdm.getDistanceKm()
        val moveMins = hdm.getMoveMinutes().toDouble()

        return if (moveMins > 0 && distanceKm > 0.0) {
            val speed = distanceKm / (moveMins / 60.0)
            caloriesFromMet(walkingMetForSpeed(speed), weightKg, moveMins)
        } else if (steps > 0) {
            val estimatedMinutes = hdm.calculateDurationMin(steps).coerceAtLeast(1).toDouble()
            val estimatedDistance = hdm.calculateDistanceKm(steps)
            val speed = estimatedDistance / (estimatedMinutes / 60.0)
            caloriesFromMet(walkingMetForSpeed(speed), weightKg, estimatedMinutes)
        } else {
            0.0
        }
    }

    private fun calculateWorkoutBurn(context: Context, date: String, weightKg: Double): Double {
        val doneIds = RoutineProgressStore.getDoneIds(context, date)
        val doneExercises = PlanManager(context)
            .getExercisesForDate(date)
            .filter { it.id.toString() in doneIds }

        var total = 0.0
        doneExercises.forEach { exercise ->
            val durationMins = estimateExerciseDurationMinutes(exercise)
            val baseMet = exercise.estimatedMet.takeIf { it > 0.0 } ?: metForExercise(exercise)
            val adjustedMet = baseMet * (0.8 + (exercise.intensity / 100.0) * 0.4)
            val burn = caloriesFromMet(adjustedMet, weightKg, durationMins)
            total += burn
            Log.d("WellnessEngine", "Workout burn ${exercise.name}: $burn kcal")
        }

        return total
    }

    private fun walkingMetForSpeed(speedKmH: Double): Double {
        return when {
            speedKmH > 6.0 -> MET_TABLE["walking_brisk"]!!
            speedKmH > 4.0 -> MET_TABLE["walking_avg"]!!
            else -> MET_TABLE["walking_slow"]!!
        }
    }

    private fun caloriesFromMet(met: Double, weightKg: Double, minutes: Double): Double {
        return (met * 3.5 * weightKg / 200.0) * minutes
    }

    fun shouldEnrichWorkoutMet(ex: Exercise): Boolean {
        return ex.estimatedMet <= 0.0 && !hasSpecificExerciseMatch(ex)
    }

    fun getLocalWorkoutMet(ex: Exercise): Double {
        return metForExercise(ex)
    }

    private fun metForExercise(ex: Exercise): Double {
        val name = "${ex.name} ${ex.targetArea}".lowercase(Locale.US)
        return when {
            name.contains("burpee") -> MET_TABLE["burpees"]!!
            name.contains("jumping jack") -> MET_TABLE["jumping_jacks"]!!
            name.contains("skip") || name.contains("jump rope") -> MET_TABLE["skipping"]!!
            name.contains("run") || name.contains("jog") -> MET_TABLE["running"]!!
            name.contains("cycle") || name.contains("bike") -> MET_TABLE["cycling"]!!
            name.contains("swim") -> MET_TABLE["swimming"]!!
            name.contains("hiit") || name.contains("tabata") -> MET_TABLE["hiit"]!!
            name.contains("cardio") || name.contains("aerobic") -> MET_TABLE["cardio"]!!
            name.contains("push") -> MET_TABLE["pushups"]!!
            name.contains("squat") -> MET_TABLE["squats"]!!
            name.contains("lunge") -> MET_TABLE["lunges"]!!
            name.contains("plank") -> MET_TABLE["plank"]!!
            name.contains("crunch") || name.contains("sit up") -> MET_TABLE["crunches"]!!
            name.contains("yoga") -> MET_TABLE["yoga"]!!
            name.contains("pilates") -> MET_TABLE["pilates"]!!
            name.contains("stretch") -> MET_TABLE["stretching"]!!
            name.contains("dance") || name.contains("zumba") -> MET_TABLE["dance"]!!
            name.contains("stair") -> MET_TABLE["stairs"]!!
            ex.intensity >= 75 -> MET_TABLE["weightlifting_heavy"]!!
            else -> MET_TABLE["weightlifting_light"]!!
        }
    }

    private fun hasSpecificExerciseMatch(ex: Exercise): Boolean {
        val name = "${ex.name} ${ex.targetArea}".lowercase(Locale.US)
        return listOf(
            "burpee",
            "jumping jack",
            "skip",
            "jump rope",
            "run",
            "jog",
            "cycle",
            "bike",
            "swim",
            "hiit",
            "tabata",
            "cardio",
            "aerobic",
            "push",
            "squat",
            "lunge",
            "plank",
            "crunch",
            "sit up",
            "yoga",
            "pilates",
            "stretch",
            "dance",
            "zumba",
            "stair"
        ).any { name.contains(it) }
    }

    private fun estimateExerciseDurationMinutes(ex: Exercise): Double {
        val repsText = ex.reps.lowercase(Locale.US).trim()

        val explicitMinutes = Regex("""(\d+(?:\.\d+)?)\s*(min|mins|minute|minutes)\b""")
            .find(repsText)
            ?.groupValues
            ?.getOrNull(1)
            ?.toDoubleOrNull()
        if (explicitMinutes != null) {
            return (explicitMinutes * ex.sets.coerceAtLeast(1)).coerceAtLeast(1.0)
        }

        val explicitSeconds = Regex("""(\d+(?:\.\d+)?)\s*(sec|secs|second|seconds)\b""")
            .find(repsText)
            ?.groupValues
            ?.getOrNull(1)
            ?.toDoubleOrNull()
        if (explicitSeconds != null) {
            return ((explicitSeconds / 60.0) * ex.sets.coerceAtLeast(1)).coerceAtLeast(1.0)
        }

        val reps = Regex("""\d+""").find(repsText)?.value?.toIntOrNull() ?: 10
        val totalReps = reps * ex.sets.coerceAtLeast(1)
        val secondsPerRep = when {
            ex.name.lowercase(Locale.US).contains("plank") -> 1.0
            ex.intensity >= 75 -> 3.0
            else -> 4.0
        }
        val activeMinutes = (totalReps * secondsPerRep) / 60.0
        val restMinutes = (ex.sets.coerceAtLeast(1) - 1).coerceAtLeast(0) * 0.75
        return (activeMinutes + restMinutes).coerceAtLeast(1.0)
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

        if (lastWeekSteps == 0L) return "Keep moving to start seeing your weekly trends!"

        val diff = ((currentWeekSteps - lastWeekSteps).toDouble() / lastWeekSteps) * 100
        return when {
            diff > 10 -> "You're crushing it! You walked ${diff.toInt()}% more this week."
            diff < -10 -> "A bit slower this week. Let's aim to beat last week's total!"
            else -> "Steady progress! You're maintaining a consistent pace."
        }
    }

    data class Milestone(val id: String, val title: String, val emoji: String, val description: String)

    val milestones = listOf(
        Milestone("century_walker", "Century Walker", "Walking", "100k total steps achieved!"),
        Milestone("hydration_hero", "Hydration Hero", "Water", "7-day water streak!"),
        Milestone("sleep_master", "Sleep Master", "Sleep", "Perfect sleep for 30 days!"),
        Milestone("early_bird", "Early Bird", "Morning", "5 consecutive 6 AM check-ins!")
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
