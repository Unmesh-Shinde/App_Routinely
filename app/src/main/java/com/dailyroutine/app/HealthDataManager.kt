package com.dailyroutine.app

import android.content.Context
import android.content.SharedPreferences

class HealthDataManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("health_data_pref", Context.MODE_PRIVATE)

    companion object {
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
    }

    fun getConnectedAppName(): String = prefs.getString(KEY_SOURCE_APP, "None") ?: "None"
    fun setConnectedAppName(name: String) = prefs.edit().putString(KEY_SOURCE_APP, name).apply()

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
        val current = getWaterIntake(date)
        prefs.edit().putFloat(KEY_WATER_PREFIX + date, (current + amount).toFloat()).apply()
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
    fun getWeight(): String = if (isConnected()) prefs.getString(KEY_WEIGHT, "0 kg") ?: "0 kg" else "0 kg"
}
