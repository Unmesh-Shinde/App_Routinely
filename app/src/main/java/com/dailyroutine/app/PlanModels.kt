package com.dailyroutine.app

import java.io.Serializable

interface PlannableItem : Serializable {
    val id: Int
    val name: String
    val hour: Int
    val minute: Int
}

interface PlannableTemplate<T : PlannableItem> : Serializable {
    val id: String
    val name: String
    val durationDays: Int
    val itemsByDayOffset: MutableMap<Int, MutableList<T>>
}

// --- Diet Plan Models ---

data class MealNutritionInfo(
    // Nullable values preserve the difference between an estimated zero and a field Gemini omitted.
    val calories: Int? = null,
    val carbsG: Double? = null,
    val proteinG: Double? = null,
    val fatG: Double? = null,
    val fiberG: Double? = null,
    val fatBreakdown: Map<String, String> = emptyMap(),
    val carbBreakdown: Map<String, String> = emptyMap(),
    val vitamins: Map<String, String> = emptyMap(),
    val minerals: Map<String, String> = emptyMap(),
    val aminoAcids: Map<String, String> = emptyMap(),
    val antioxidants: Map<String, String> = emptyMap(),
    val otherNutrients: Map<String, String> = emptyMap()
) : Serializable

data class Meal(
    override val id: Int = (System.currentTimeMillis() % Int.MAX_VALUE).toInt() + java.util.Random().nextInt(1000),
    override val name: String = "",
    val description: String = "",
    override val hour: Int = 12,
    override val minute: Int = 0,
    val isReminderEnabled: Boolean = true,
    val mealType: String = "Lunch", // Breakfast, Lunch, Dinner, Snack
    val calories: Int? = null,
    val nutritionInfo: MealNutritionInfo? = null,
    val isWaterIntakeSummary: Boolean = false,
    val isPendingOvernightRetry: Boolean = false,
    val queuedTimestamp: Long = 0L,
    val manualRetryAttempted: Boolean = false,
    val nutritionErrorCode: String? = null,
    val addedTimestamp: Long = System.currentTimeMillis(),
    val editCount: Int = 0,
    val nutritionDayRetriesUsed: Int = 0,
    val nutritionOvernightRetriesUsed: Int = 0,
    val nutritionRetryStage: String? = null,
    val nextNutritionRetryAt: Long = 0L,
    val nutritionAutoRetriesExhausted: Boolean = false,
    val nutritionOvernightWindowStartAt: Long = 0L
) : PlannableItem {
    fun formatTime(): String {
        val h = if (hour == 0 || hour == 12) 12 else hour % 12
        val amPm = if (hour < 12) "AM" else "PM"
        return "%02d:%02d %s".format(h, minute, amPm)
    }

    val displayCalories: Int?
        get() = nutritionInfo?.calories ?: calories?.takeIf { it > 0 }
}

data class DietPlan(
    val dailyMeals: MutableMap<String, MutableList<Meal>> = mutableMapOf() // Date (yyyy-MM-dd) -> List of meals
) : Serializable

data class MealTemplate(
    override val id: String,
    override val name: String,
    override val durationDays: Int,
    val mealsByDayOffset: MutableMap<Int, MutableList<Meal>> = mutableMapOf()
) : PlannableTemplate<Meal> {
    override val itemsByDayOffset: MutableMap<Int, MutableList<Meal>>
        get() = mealsByDayOffset
}

data class AppliedTemplateRange(
    val templateId: String,
    val templateName: String,
    val startDate: String,
    val endDate: String
) : Serializable

// --- Workout Plan Models ---


data class Exercise(
    override val id: Int = (System.currentTimeMillis() % Int.MAX_VALUE).toInt() + java.util.Random().nextInt(1000),
    override val name: String = "",
    val sets: Int = 3,
    val reps: String = "10",
    override val hour: Int = 8,
    override val minute: Int = 0,
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
) : PlannableItem {
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
    override val id: String,
    override val name: String,
    override val durationDays: Int,
    val exercisesByDayOffset: MutableMap<Int, MutableList<Exercise>> = mutableMapOf()
) : PlannableTemplate<Exercise> {
    override val itemsByDayOffset: MutableMap<Int, MutableList<Exercise>>
        get() = exercisesByDayOffset
}
