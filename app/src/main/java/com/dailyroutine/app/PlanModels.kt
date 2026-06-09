package com.dailyroutine.app

import java.io.Serializable

// --- Diet Plan Models ---

enum class PlanDuration(val label: String, val totalDays: Int) {
    WEEKLY("Weekly", 7),
    BI_WEEKLY("Bi-Weekly", 14),
    MONTHLY("Monthly", 30)
}

data class Meal(
    val id: Int = System.currentTimeMillis().toInt(),
    val name: String,
    val description: String = "",
    val hour: Int,
    val minute: Int,
    val isReminderEnabled: Boolean = true,
    val mealType: String = "Lunch" // Breakfast, Lunch, Dinner, Snack
) : Serializable {
    fun formatTime(): String {
        val h = if (hour == 0 || hour == 12) 12 else hour % 12
        val amPm = if (hour < 12) "AM" else "PM"
        return "%02d:%02d %s".format(h, minute, amPm)
    }
}

data class DietPlan(
    val duration: PlanDuration,
    val dailyMeals: MutableMap<Int, MutableList<Meal>> = mutableMapOf() // Day (1..totalDays) -> List of meals
) : Serializable

// --- Workout Plan Models ---

enum class ChallengeDuration(val label: String, val days: Int) {
    WEEKLY("Weekly", 7),
    TEN_DAYS("10 Days", 10),
    FIFTEEN_DAYS("15 Days", 15),
    THIRTY_DAYS("30 Days", 30)
}

data class Exercise(
    val id: Int = System.currentTimeMillis().toInt(),
    val name: String,
    val sets: Int = 3,
    val reps: String = "10",
    val hour: Int,
    val minute: Int,
    val isReminderEnabled: Boolean = true,
    val targetArea: String = "Full Body",
    val intensity: Int = 50 // 0..100
) : Serializable {
    fun formatTime(): String {
        val h = if (hour == 0 || hour == 12) 12 else hour % 12
        val amPm = if (hour < 12) "AM" else "PM"
        return "%02d:%02d %s".format(h, minute, amPm)
    }
}

data class WorkoutPlan(
    val duration: ChallengeDuration,
    val dailyExercises: MutableMap<Int, MutableList<Exercise>> = mutableMapOf() // Day (1..days) -> List of exercises
) : Serializable
