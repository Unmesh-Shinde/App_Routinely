package com.dailyroutine.app

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

class PlanManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("plans_pref", Context.MODE_PRIVATE)
    private val gson = Gson()

    // --- Calendar-Based Diet Plan Management ---

    private fun getFullDietPlan(): DietPlan {
        val json = prefs.getString("diet_calendar_data", null)
        return if (json != null) {
            val type = object : TypeToken<DietPlan>() {}.type
            gson.fromJson(json, type) ?: DietPlan()
        } else {
            DietPlan()
        }
    }

    fun getMealsForDate(date: String): MutableList<Meal> {
        return getFullDietPlan().dailyMeals[date] ?: mutableListOf()
    }

    fun saveMealForDate(date: String, meal: Meal) {
        val plan = getFullDietPlan()
        val list = plan.dailyMeals.getOrPut(date) { mutableListOf() }
        val idx = list.indexOfFirst { it.id == meal.id }
        if (idx >= 0) list[idx] = meal else list.add(meal)
        
        prefs.edit().putString("diet_calendar_data", gson.toJson(plan)).apply()
    }

    fun deleteMealForDate(date: String, meal: Meal) {
        val plan = getFullDietPlan()
        plan.dailyMeals[date]?.removeIf { it.id == meal.id }
        prefs.edit().putString("diet_calendar_data", gson.toJson(plan)).apply()
    }

    // --- Calendar-Based Workout Plan Management ---

    private fun getFullWorkoutPlan(): WorkoutPlan {
        val json = prefs.getString("workout_calendar_data", null)
        return if (json != null) {
            val type = object : TypeToken<WorkoutPlan>() {}.type
            gson.fromJson(json, type) ?: WorkoutPlan()
        } else {
            WorkoutPlan()
        }
    }

    fun getExercisesForDate(date: String): MutableList<Exercise> {
        return getFullWorkoutPlan().dailyExercises[date] ?: mutableListOf()
    }

    fun saveExerciseForDate(date: String, ex: Exercise) {
        val plan = getFullWorkoutPlan()
        val list = plan.dailyExercises.getOrPut(date) { mutableListOf() }
        val idx = list.indexOfFirst { it.id == ex.id }
        if (idx >= 0) list[idx] = ex else list.add(ex)
        prefs.edit().putString("workout_calendar_data", gson.toJson(plan)).apply()
    }

    fun deleteExerciseForDate(date: String, ex: Exercise) {
        val plan = getFullWorkoutPlan()
        plan.dailyExercises[date]?.removeIf { it.id == ex.id }
        prefs.edit().putString("workout_calendar_data", gson.toJson(plan)).apply()
    }

    fun addQuickIndianDiet(date: String) {
        saveMealForDate(date, Meal(name = "Oats & Milk", mealType = "Breakfast", hour = 8, minute = 30))
        saveMealForDate(date, Meal(name = "Roti & Sabzi", mealType = "Lunch", hour = 13, minute = 0))
        saveMealForDate(date, Meal(name = "Dal & Rice", mealType = "Dinner", hour = 20, minute = 30))
    }

    // Updated sync logic for date-based exercises
    fun syncExerciseReminder(context: Context, ex: Exercise, date: String) {
        val mgr = ReminderManager(context)
        if (!ex.isReminderEnabled) {
            mgr.deleteReminder(Reminder(id = ex.id))
            return
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val calDate = sdf.parse(date) ?: Date()
        val calendar = Calendar.getInstance().apply { time = calDate }
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

        mgr.saveReminder(Reminder(
            id = ex.id,
            title = "Exercise: ${ex.name}",
            type = ReminderType.EXERCISE,
            hour = ex.hour,
            minute = ex.minute,
            repeatDays = listOf(dayOfWeek),
            isHidden = true
        ))
    }

    fun syncMealReminder(context: Context, meal: Meal, date: String) {
        val mgr = ReminderManager(context)
        if (!meal.isReminderEnabled) {
            mgr.deleteReminder(Reminder(id = meal.id))
            return
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val calDate = sdf.parse(date) ?: Date()
        val calendar = Calendar.getInstance().apply { time = calDate }
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

        mgr.saveReminder(Reminder(
            id = meal.id,
            title = "Meal: ${meal.name}",
            type = ReminderType.MEAL,
            hour = meal.hour,
            minute = meal.minute,
            repeatDays = listOf(dayOfWeek),
            isHidden = true,
            dishType = meal.name,
            ingredients = meal.description
        ))
    }

    // --- Legacy Challenge Management (If needed, can be refactored to use dates) ---

    fun getWorkoutPlan(duration: ChallengeDuration): WorkoutPlan {
        // This could be deprecated or refactored to return a challenge template
        return WorkoutPlan()
    }

    fun saveWorkoutPlan(plan: WorkoutPlan) {
        // Logic for saving a whole plan/challenge
    }
}
