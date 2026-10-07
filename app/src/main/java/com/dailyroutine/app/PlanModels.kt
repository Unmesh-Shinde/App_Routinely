package com.dailyroutine.app

import java.io.Serializable

// --- Diet Plan Models ---

data class MealNutritionInfo(
    val calories: Int = 0,
    val carbsG: Double = 0.0,
    val proteinG: Double = 0.0,
    val fatG: Double = 0.0,
    val fiberG: Double = 0.0,
    val fatBreakdown: Map<String, String> = emptyMap(),
    val carbBreakdown: Map<String, String> = emptyMap(),
    val vitamins: Map<String, String> = emptyMap(),
    val minerals: Map<String, String> = emptyMap(),
    val aminoAcids: Map<String, String> = emptyMap(),
    val antioxidants: Map<String, String> = emptyMap(),
    val otherNutrients: Map<String, String> = emptyMap()
) : Serializable

data class Meal(
    val id: Int = (System.currentTimeMillis() % Int.MAX_VALUE).toInt() + java.util.Random().nextInt(1000),
    val name: String = "",
    val description: String = "",
    val hour: Int = 12,
    val minute: Int = 0,
    val isReminderEnabled: Boolean = true,
    val mealType: String = "Lunch", // Breakfast, Lunch, Dinner, Snack
    val calories: Int = 0,
    val nutritionInfo: MealNutritionInfo? = null
) : Serializable {
    fun formatTime(): String {
        val h = if (hour == 0 || hour == 12) 12 else hour % 12
        val amPm = if (hour < 12) "AM" else "PM"
        return "%02d:%02d %s".format(h, minute, amPm)
    }

    val displayCalories: Int
        get() = nutritionInfo?.calories ?: calories
}

data class DietPlan(
    val dailyMeals: MutableMap<String, MutableList<Meal>> = mutableMapOf() // Date (yyyy-MM-dd) -> List of meals
) : Serializable

data class MealTemplate(
    val id: String,
    val name: String,
    val durationDays: Int,
    val mealsByDayOffset: MutableMap<Int, MutableList<Meal>> = mutableMapOf()
) : Serializable

data class AppliedTemplateRange(
    val templateId: String,
    val templateName: String,
    val startDate: String,
    val endDate: String
) : Serializable

// --- Workout Plan Models ---


data class Exercise(
    val id: Int = (System.currentTimeMillis() % Int.MAX_VALUE).toInt() + java.util.Random().nextInt(1000),
    val name: String = "",
    val sets: Int = 3,
    val reps: String = "10",
    val hour: Int = 8,
    val minute: Int = 0,
    val isReminderEnabled: Boolean = true,
    val targetArea: String = "Full Body",
    val intensity: Int = 50, // 0..100
    val estimatedMet: Double = 0.0,
    val metSource: String = "local",
    val exerciseType: String? = null,
    val effortLabel: String? = null,
    val durationSeconds: Int = 0,
    val restSeconds: Int = 0,
    val rounds: Int = 0,
    val workSeconds: Int = 0,
    val addedWeightKg: Double = 0.0,
    val distanceKm: Double = 0.0
) : Serializable {
    fun formatTime(): String {
        val h = if (hour == 0 || hour == 12) 12 else hour % 12
        val amPm = if (hour < 12) "AM" else "PM"
        return "%02d:%02d %s".format(h, minute, amPm)
    }
}

data class WorkoutPlan(
    val dailyExercises: MutableMap<String, MutableList<Exercise>> = mutableMapOf() // Date (yyyy-MM-dd) -> List of exercises
) : Serializable

data class WorkoutTemplate(
    val id: String,
    val name: String,
    val durationDays: Int,
    val exercisesByDayOffset: MutableMap<Int, MutableList<Exercise>> = mutableMapOf()
) : Serializable
