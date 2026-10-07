package com.dailyroutine.app

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate
import java.time.format.DateTimeParseException

class HealthDataManager(private val context: Context) {
    fun contextForEngine(): Context = context
    private val prefs: SharedPreferences = context.getSharedPreferences("health_data_pref", Context.MODE_PRIVATE)
    private val repository by lazy { HealthMetricsRepository.create(context.applicationContext) }

    companion object {
        const val SYNC_HISTORY_DAYS = 180

        private const val KEY_IS_CONNECTED = "is_fitness_connected"
        private const val KEY_STEPS = "steps_count"
        private const val KEY_SLEEP = "sleep_hours"
        private const val KEY_CALORIES = "calories_burnt"
        private const val KEY_WEIGHT = "current_weight"
        private const val KEY_CALORIE_GOAL = "daily_calorie_goal"
        private const val KEY_STEP_GOAL = "daily_step_goal"
        private const val KEY_DISTANCE_GOAL = "daily_distance_goal"
        private const val KEY_WATER_PREFIX = "water_intake_"
        private const val KEY_WEIGHT_HISTORY = "weight_history_data"
        private const val KEY_SOURCE_APP = "connected_health_app_name"
        private const val KEY_SOURCE_PKG = "connected_health_app_package"
        private const val KEY_MOVE_MINS = "move_minutes_count"
        private const val KEY_HEART_POINTS = "heart_points_count"
        private const val KEY_LAST_SYNC = "last_background_sync_time"
        private const val KEY_DISTANCE_VAL = "distance_val"
        private const val KEY_INITIAL_HISTORY_SYNC_DONE = "initial_history_sync_done"
        private const val KEY_INITIAL_HEART_POINTS_HISTORY_SYNC_DONE = "initial_heart_points_server_history_sync_done_v2"
        private const val KEY_HIST_STEPS_PREFIX = "hist_steps_"
        private const val KEY_HIST_STEPS_SYNCED_PREFIX = "hist_steps_synced_"
        private const val KEY_HIST_SLEEP_PREFIX = "hist_sleep_"
        private const val KEY_HIST_CALS_PREFIX = "hist_cals_"
        private const val KEY_HIST_HEART_POINTS_PREFIX = "hist_heart_pts_"
        private const val KEY_HIST_HEART_POINTS_SYNCED_PREFIX = "hist_heart_pts_synced_"
        private const val KEY_HIST_DISTANCE_PREFIX = "hist_dist_"
        private const val KEY_HIST_ACTIVE_CALS_PREFIX = "hist_active_cals_"
        private const val KEY_WATER_HISTORY_PREFIX = "water_intake_"

        private val LOCK = Any()

        /**
         * Distance has been stored in kilometres since the Health Connect sync was
         * introduced. Older versions stored metres, so normalise those records at
         * the boundary rather than allowing a single legacy value to distort a chart.
         */
        fun normalizeDistanceKm(rawDistance: Double?): Double {
            val distance = rawDistance ?: return 0.0
            if (!distance.isFinite() || distance <= 0.0) return 0.0
            return if (distance > 100.0) distance / 1000.0 else distance
        }
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

    fun getHeartPoints(): Int = prefs.getInt(KEY_HEART_POINTS, 0)
    fun setHeartPoints(points: Int) = prefs.edit().putInt(KEY_HEART_POINTS, points).apply()

    fun getConnectedAppName(): String = prefs.getString(KEY_SOURCE_APP, "None") ?: "None"
    fun setConnectedAppName(name: String) = prefs.edit().putString(KEY_SOURCE_APP, name).apply()

    fun getConnectedAppPackage(): String? = prefs.getString(KEY_SOURCE_PKG, null)
    fun setConnectedAppPackage(pkg: String) {
        val previousPkg = getConnectedAppPackage()
        prefs.edit()
            .putString(KEY_SOURCE_PKG, pkg)
            .putBoolean(KEY_INITIAL_HISTORY_SYNC_DONE, previousPkg == pkg && isInitialHistorySyncDone())
            .apply()
    }

    fun isInitialHistorySyncDone(): Boolean = prefs.getBoolean(KEY_INITIAL_HISTORY_SYNC_DONE, false)
    fun setInitialHistorySyncDone(done: Boolean) = prefs.edit().putBoolean(KEY_INITIAL_HISTORY_SYNC_DONE, done).apply()

    fun isInitialHeartPointsHistorySyncDone(): Boolean = prefs.getBoolean(KEY_INITIAL_HEART_POINTS_HISTORY_SYNC_DONE, false)
    fun setInitialHeartPointsHistorySyncDone(done: Boolean) =
        prefs.edit().putBoolean(KEY_INITIAL_HEART_POINTS_HISTORY_SYNC_DONE, done).apply()

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

    fun getDailyDistanceGoal(): Float = prefs.getFloat(KEY_DISTANCE_GOAL, 5.0f)
    fun setDailyDistanceGoal(goal: Float) = prefs.edit().putFloat(KEY_DISTANCE_GOAL, goal).apply()

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

    suspend fun getWeightRoom(date: String): Double {
        return repository.getMetric(date)?.weightKg ?: getWeight(date)
    }

    suspend fun getMetricsMap(startDate: String, endDate: String): Map<String, DailyHealthMetricEntity> {
        return repository.getMetricsMap(startDate, endDate)
    }

    fun saveWeight(date: String, weight: Double) {
        synchronized(LOCK) {
            val history = getWeightHistoryLocked().toMutableMap()
            history[date] = weight
            val json = com.google.gson.Gson().toJson(history)
            prefs.edit().putString(KEY_WEIGHT_HISTORY, json).apply()
        }
    }

    private fun getWeightHistory(): Map<String, Double> {
        synchronized(LOCK) {
            return getWeightHistoryLocked()
        }
    }

    private fun getWeightHistoryLocked(): Map<String, Double> {
        val json = prefs.getString(KEY_WEIGHT_HISTORY, null) ?: return emptyMap()
        val type = object : com.google.gson.reflect.TypeToken<Map<String, Double>>() {}.type
        return com.google.gson.Gson().fromJson(json, type) ?: emptyMap()
    }

    fun calculateDistanceKm(steps: Int): Double {
        val height = UserPreferencesStore.getUserHeight(context)
        val gender = UserPreferencesStore.getUserGender(context)
        val strideMultiplier = if (gender == "Male") 0.415 else 0.413
        val strideMeters = (height * strideMultiplier) / 100.0
        
        if (strideMeters > 0.3) {
            return (steps * strideMeters) / 1000.0
        }
        // Fallback to average stride length ~0.76m
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
        prefs.edit()
            .putLong(KEY_HIST_STEPS_PREFIX + date, count)
            .putBoolean(KEY_HIST_STEPS_SYNCED_PREFIX + date, true)
            .apply()
    }
    fun getHistoricalSteps(date: String): Long = prefs.getLong(KEY_HIST_STEPS_PREFIX + date, 0L)
    
    suspend fun getHistoricalStepsRoom(date: String): Long {
        return repository.getMetric(date)?.steps ?: getHistoricalSteps(date)
    }

    fun isHistoricalStepsSynced(date: String): Boolean = prefs.getBoolean(KEY_HIST_STEPS_SYNCED_PREFIX + date, false)

    fun countHistoricalStepSyncedDays(days: Int = SYNC_HISTORY_DAYS): Int {
        val today = LocalDate.now()
        return (0 until days).count { offset ->
            isHistoricalStepsSynced(today.minusDays(offset.toLong()).toString())
        }
    }

    fun isStepHistoryComplete(days: Int = SYNC_HISTORY_DAYS): Boolean {
        return countHistoricalStepSyncedDays(days) >= days
    }

    fun saveHistoricalSleep(date: String, hours: Double) {
        prefs.edit().putFloat("hist_sleep_$date", hours.toFloat()).apply()
    }
    fun getHistoricalSleep(date: String): Double = prefs.getFloat("hist_sleep_$date", 0.0f).toDouble()

    suspend fun getHistoricalSleepRoom(date: String): Double {
        return repository.getMetric(date)?.sleepHours ?: getHistoricalSleep(date)
    }

    fun saveHistoricalCalories(date: String, cals: Double) {
        prefs.edit().putFloat("hist_cals_$date", cals.toFloat()).apply()
    }
    fun getHistoricalCalories(date: String): Double = prefs.getFloat("hist_cals_$date", 0.0f).toDouble()

    suspend fun getHistoricalCaloriesRoom(date: String): Double {
        return repository.getMetric(date)?.totalCalories ?: getHistoricalCalories(date)
    }

    fun saveHistoricalActiveCalories(date: String, cals: Double) {
        prefs.edit().putFloat(KEY_HIST_ACTIVE_CALS_PREFIX + date, cals.toFloat()).apply()
    }
    fun getHistoricalActiveCalories(date: String): Double = prefs.getFloat(KEY_HIST_ACTIVE_CALS_PREFIX + date, 0.0f).toDouble()

    fun saveHistoricalDistance(date: String, distKm: Double) {
        prefs.edit().putFloat(KEY_HIST_DISTANCE_PREFIX + date, distKm.toFloat()).apply()
    }
    fun getHistoricalDistance(date: String): Double {
        val rawDistance = try {
            prefs.getFloat(KEY_HIST_DISTANCE_PREFIX + date, 0.0f).toDouble()
        } catch (e: ClassCastException) {
            // Migration: Read legacy String and convert to Float
            val legacy = prefs.getString(KEY_HIST_DISTANCE_PREFIX + date, null)
            val converted = legacy?.replace(" km", "")?.toFloatOrNull() ?: 0.0f
            prefs.edit().putFloat(KEY_HIST_DISTANCE_PREFIX + date, converted).apply()
            converted.toDouble()
        }
        
        val normalizedDistance = normalizeDistanceKm(rawDistance)
        // Rewrite legacy metre values so all future consumers read the same unit.
        if (normalizedDistance != rawDistance && normalizedDistance > 0.0) {
            prefs.edit().putFloat(KEY_HIST_DISTANCE_PREFIX + date, normalizedDistance.toFloat()).apply()
        }
        return normalizedDistance
    }

    /** Cleans legacy metre-valued Room records in the requested historical window. */
    suspend fun normalizeStoredDistanceData(startDate: String, endDate: String) {
        repository.getMetricsBetween(startDate, endDate).forEach { metric ->
            val rawDistance = metric.distanceKm ?: return@forEach
            val normalizedDistance = normalizeDistanceKm(rawDistance)
            if (normalizedDistance > 0.0 && normalizedDistance != rawDistance) {
                repository.upsertDistance(metric.date, normalizedDistance)
            }
        }
    }

    suspend fun getHistoricalDistanceRoom(date: String): Double {
        return repository.getMetric(date)?.distanceKm ?: getHistoricalDistance(date)
    }

    fun saveHistoricalHeartPoints(date: String, pts: Double) {
        prefs.edit()
            .putFloat(KEY_HIST_HEART_POINTS_PREFIX + date, pts.toFloat())
            .putBoolean(KEY_HIST_HEART_POINTS_SYNCED_PREFIX + date, true)
            .apply()
    }
    fun getHistoricalHeartPoints(date: String): Double = prefs.getFloat("hist_heart_pts_$date", 0.0f).toDouble()

    suspend fun getHistoricalHeartPointsRoom(date: String): Double {
        return repository.getMetric(date)?.heartPoints ?: getHistoricalHeartPoints(date)
    }

    fun isHistoricalHeartPointsSynced(date: String): Boolean = prefs.getBoolean(KEY_HIST_HEART_POINTS_SYNCED_PREFIX + date, false)

    fun countHistoricalHeartPointsSyncedDays(days: Int = SYNC_HISTORY_DAYS): Int {
        val today = LocalDate.now()
        return (0 until days).count { offset ->
            isHistoricalHeartPointsSynced(today.minusDays(offset.toLong()).toString())
        }
    }

    fun isHeartPointsHistoryComplete(days: Int = SYNC_HISTORY_DAYS): Boolean {
        return countHistoricalHeartPointsSyncedDays(days) >= days
    }

    fun pruneHistoricalData(retentionDays: Int = SYNC_HISTORY_DAYS) {
        val cutoff = LocalDate.now().minusDays((retentionDays - 1).toLong())
        val historicalPrefixes = listOf(
            KEY_HIST_STEPS_SYNCED_PREFIX,
            KEY_HIST_STEPS_PREFIX,
            KEY_HIST_SLEEP_PREFIX,
            KEY_HIST_CALS_PREFIX,
            KEY_HIST_ACTIVE_CALS_PREFIX,
            KEY_HIST_HEART_POINTS_SYNCED_PREFIX,
            KEY_HIST_HEART_POINTS_PREFIX,
            KEY_HIST_DISTANCE_PREFIX,
            KEY_WATER_HISTORY_PREFIX
        )

        synchronized(LOCK) {
            val editor = prefs.edit()
            prefs.all.keys.forEach { key ->
                val matchedPrefix = historicalPrefixes.firstOrNull { key.startsWith(it) }
                if (matchedPrefix != null && isDateBeforeCutoff(key.removePrefix(matchedPrefix), cutoff)) {
                    editor.remove(key)
                }
            }

            val prunedWeightHistory = getWeightHistoryLocked().filterKeys { date ->
                !isDateBeforeCutoff(date, cutoff)
            }
            editor.putString(KEY_WEIGHT_HISTORY, com.google.gson.Gson().toJson(prunedWeightHistory))
            editor.apply()
        }
    }

    private fun isDateBeforeCutoff(dateText: String, cutoff: LocalDate): Boolean {
        return try {
            LocalDate.parse(dateText).isBefore(cutoff)
        } catch (_: DateTimeParseException) {
            false
        }
    }
}
