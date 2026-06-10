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

    // --- Workout Plan Management ---

    fun getWorkoutPlan(duration: ChallengeDuration): WorkoutPlan {
        val key = "workout_${duration.name}"
        val json = prefs.getString(key, null)
        return if (json != null) {
            val type = object : TypeToken<WorkoutPlan>() {}.type
            gson.fromJson(json, type) ?: WorkoutPlan(duration)
        } else {
            WorkoutPlan(duration)
        }
    }

    fun saveWorkoutPlan(plan: WorkoutPlan) {
        val key = "workout_${plan.duration.name}"
        prefs.edit().putString(key, gson.toJson(plan)).apply()
    }

    // Updated sync logic for date-based meals
    fun syncMealReminder(context: Context, meal: Meal, date: String) {
        val mgr = ReminderManager(context)
        if (!meal.isReminderEnabled) {
            mgr.deleteReminder(Reminder(id = meal.id))
            return
        }
        
        // For individual calendar dates, we set it to trigger on that specific day
        // For simplicity in our current ReminderManager (which uses repeatDays), 
        // we'll map this date to its weekday, or treat it as a recurring daily reminder 
        // if it's meant to be a general routine.
        
        // However, since it's a specific DATE, we should ideally add date support to ReminderManager.
        // For now, let's keep it as "Every Day" if it's in the calendar, but marked with the date in title.
        
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val calDate = sdf.parse(date) ?: Date()
        val calendar = Calendar.getInstance().apply { time = calDate }
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) // 1=Sun, 2=Mon...

        mgr.saveReminder(Reminder(
            id = meal.id,
            title = "Meal: ${meal.name}",
            type = ReminderType.MEAL,
            hour = meal.hour,
            minute = meal.minute,
            repeatDays = listOf(dayOfWeek), // Only on that specific weekday
            isHidden = true,
            dishType = meal.name,
            ingredients = meal.description
        ))
    }

    fun syncExerciseReminder(context: Context, ex: Exercise, day: Int, duration: ChallengeDuration) {
        val mgr = ReminderManager(context)
        if (!ex.isReminderEnabled) {
            mgr.deleteReminder(Reminder(id = ex.id))
            return
        }

        mgr.saveReminder(Reminder(
            id = ex.id,
            title = "Exercise: ${ex.name}",
            type = ReminderType.EXERCISE,
            hour = ex.hour,
            minute = ex.minute,
            repeatDays = listOf(1, 2, 3, 4, 5, 6, 7), // Every day for challenges
            isHidden = true
        ))
    }
}
