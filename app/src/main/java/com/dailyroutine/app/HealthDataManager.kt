package com.dailyroutine.app

import android.content.Context
import android.content.SharedPreferences

class HealthDataManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("health_data_pref", Context.MODE_PRIVATE)

    companion object {
        const val SYNC_HISTORY_DAYS = 180

        private const val KEY_IS_CONNECTED = "is_fitness_connected"
        private const val KEY_STEPS = "steps_count"
        private const val KEY_SLEEP = "sleep_hours"
        private const val KEY_CALORIES = "calories_burnt"
        private const val KEY_WEIGHT = "current_weight"
        private const val KEY_CALORIE_GOAL = "daily_calorie_goal"
        private const val KEY_STEP_GOAL = "daily_step_goal"
        private const val KEY_WATER_PREFIX = "water_intake_"
        private const val KEY_WEIGHT_HISTORY = "weight_history_data"
        private const val KEY_SOURCE_APP = "connected_health_app_name"
        private const val KEY_SOURCE_PKG = "connected_health_app_package"
        private const val KEY_MOVE_MINS = "move_minutes_count"
        private const val KEY_LAST_SYNC = "last_background_sync_time"
        private const val KEY_DISTANCE_VAL = "distance_val"
    }

    fun getDistanceKm(): Double {
        val s = prefs.getString(KEY_DISTANCE_VAL, "0.0 km") ?: "0.0"
        return s.replace(" km", "").toDoubleOrNull() ?: 0.0
    }
    fun setDistanceVal(s: String) = prefs.edit().putString(KEY_DISTANCE_VAL, s).apply()

    fun getLastSyncTime(): String = prefs.getString(KEY_LAST_SYNC, "Never") ?: "Never"
    fun setLastSyncTime(time: String) = prefs.edit().putString(KEY_LAST_SYNC, time).apply()

    fun getMoveMinutes(): Int = prefs.getInt(KEY_MOVE_MINS, 0)
    fun setMoveMinutes(mins: Int) = prefs.edit().putInt(KEY_MOVE_MINS, mins).apply()

    fun getConnectedAppName(): String = prefs.getString(KEY_SOURCE_APP, "None") ?: "None"
    fun setConnectedAppName(name: String) = prefs.edit().putString(KEY_SOURCE_APP, name).apply()

    fun getConnectedAppPackage(): String? = prefs.getString(KEY_SOURCE_PKG, null)
    fun setConnectedAppPackage(pkg: String) = prefs.edit().putString(KEY_SOURCE_PKG, pkg).apply()

    fun isConnected(): Boolean = prefs.getBoolean(KEY_IS_CONNECTED, false)

    fun setConnected(connected: Boolean) {
        prefs.edit().putBoolean(KEY_IS_CONNECTED, connected).apply()
        if (!connected) {
            // Clear data if disconnected
            prefs.edit().clear().apply()
        }
    }

    fun getDailyCalorieGoal(): Int = prefs.getInt(KEY_CALORIE_GOAL, 2000)
    fun setDailyCalorieGoal(goal: Int) = prefs.edit().putInt(KEY_CALORIE_GOAL, goal).apply()

    fun getDailyStepGoal(): Int = prefs.getInt(KEY_STEP_GOAL, 10000)
    fun setDailyStepGoal(goal: Int) = prefs.edit().putInt(KEY_STEP_GOAL, goal).apply()

    fun getWaterIntake(date: String): Double = prefs.getFloat(KEY_WATER_PREFIX + date, 0.0f).toDouble()
    fun addWaterIntake(date: String, amount: Double) {
        adjustWaterIntake(date, amount)
    }

    fun adjustWaterIntake(date: String, amount: Double): Double {
        val updated = (getWaterIntake(date) + amount).coerceAtLeast(0.0)
        prefs.edit().putFloat(KEY_WATER_PREFIX + date, updated.toFloat()).apply()
        return updated
    }

    fun getWeight(date: String): Double {
        val history = getWeightHistory()
        return history[date] ?: 0.0
    }

    fun saveWeight(date: String, weight: Double) {
        val history = getWeightHistory().toMutableMap()
        history[date] = weight
        val json = com.google.gson.Gson().toJson(history)
        prefs.edit().putString(KEY_WEIGHT_HISTORY, json).apply()
    }

    private fun getWeightHistory(): Map<String, Double> {
        val json = prefs.getString(KEY_WEIGHT_HISTORY, null) ?: return emptyMap()
        val type = object : com.google.gson.reflect.TypeToken<Map<String, Double>>() {}.type
        return com.google.gson.Gson().fromJson(json, type) ?: emptyMap()
    }

    fun calculateDistanceKm(steps: Int): Double {
        // Average stride length ~0.76m
        return (steps * 0.76) / 1000.0
    }

    fun calculateDurationMin(steps: Int): Int {
        // Average pace ~100 steps per minute
        return steps / 100
    }

    fun getSteps(): String = if (isConnected()) prefs.getString(KEY_STEPS, "0") ?: "0" else "0"
    fun getSleep(): String = if (isConnected()) prefs.getString(KEY_SLEEP, "0h") ?: "0h" else "0h"
    fun getCalories(): String = if (isConnected()) prefs.getString(KEY_CALORIES, "0") ?: "0" else "0"
    fun getWeight(): String = if (isConnected()) prefs.getString(KEY_WEIGHT, "Not Logged") ?: "Not Logged" else "Not Logged"

    fun saveHistoricalSteps(date: String, count: Long) {
        prefs.edit().putLong("hist_steps_$date", count).apply()
    }
    fun getHistoricalSteps(date: String): Long = prefs.getLong("hist_steps_$date", 0L)

    fun saveHistoricalSleep(date: String, hours: Double) {
        prefs.edit().putFloat("hist_sleep_$date", hours.toFloat()).apply()
    }
    fun getHistoricalSleep(date: String): Double = prefs.getFloat("hist_sleep_$date", 0.0f).toDouble()

    fun saveHistoricalCalories(date: String, cals: Double) {
        prefs.edit().putFloat("hist_cals_$date", cals.toFloat()).apply()
    }
    fun getHistoricalCalories(date: String): Double = prefs.getFloat("hist_cals_$date", 0.0f).toDouble()
}
