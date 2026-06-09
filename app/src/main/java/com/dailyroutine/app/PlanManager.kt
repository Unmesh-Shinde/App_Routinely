package com.dailyroutine.app

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class PlanManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("plans_pref", Context.MODE_PRIVATE)
    private val gson = Gson()

    // --- Diet Plan Management ---

    fun getDietPlan(duration: PlanDuration): DietPlan {
        val key = "diet_${duration.name}"
        val json = prefs.getString(key, null)
        return if (json != null) {
            val type = object : TypeToken<DietPlan>() {}.type
            gson.fromJson(json, type) ?: DietPlan(duration)
        } else {
            DietPlan(duration)
        }
    }

    fun saveDietPlan(plan: DietPlan) {
        val key = "diet_${plan.duration.name}"
        prefs.edit().putString(key, gson.toJson(plan)).apply()
        // Here we could also sync with ReminderManager if needed
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

    fun syncMealReminder(context: Context, meal: Meal, day: Int, duration: PlanDuration) {
        val mgr = ReminderManager(context)
        if (!meal.isReminderEnabled) {
            mgr.deleteReminder(Reminder(id = meal.id))
            return
        }
        
        // Map day to weekday if Weekly
        val days = if (duration == PlanDuration.WEEKLY) {
            listOf(if (day == 7) 1 else day + 1) // Calendar.SUNDAY is 1
        } else {
            listOf(1, 2, 3, 4, 5, 6, 7) // Every day for long plans for simplicity
        }

        mgr.saveReminder(Reminder(
            id = meal.id,
            title = "Meal: ${meal.name}",
            type = ReminderType.CUSTOM,
            hour = meal.hour,
            minute = meal.minute,
            repeatDays = days,
            isHidden = true
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
            type = ReminderType.CUSTOM,
            hour = ex.hour,
            minute = ex.minute,
            repeatDays = listOf(1, 2, 3, 4, 5, 6, 7), // Every day for challenges
            isHidden = true
        ))
    }
}
