package com.dailyroutine.app

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.LocalDate

class HealthMetricsBackfillManager(
    context: Context,
    private val roomWriter: HealthMetricsRoomWriter = HealthMetricsRoomWriter(context.applicationContext),
    private val todayProvider: () -> LocalDate = { LocalDate.now() }
) {
    private val appContext = context.applicationContext
    private val healthPrefs: SharedPreferences = appContext.getSharedPreferences(PREF_HEALTH_DATA, Context.MODE_PRIVATE)
    private val appPrefs: SharedPreferences = appContext.getSharedPreferences(PREF_APP, Context.MODE_PRIVATE)

    suspend fun backfillIfNeeded(force: Boolean = false): BackfillResult {
        // Increment SCHEMA_VERSION here if we want to force a re-backfill on schema changes
        // Version 3: Separated active/total calories
        // Version 4: Distance normalization (meters -> km)
        val currentSchemaVersion = 4
        val lastDoneVersion = appPrefs.getInt(KEY_BACKFILL_SCHEMA_VERSION, 0)

        if (!force && appPrefs.getBoolean(KEY_BACKFILL_DONE, false) && lastDoneVersion >= currentSchemaVersion) {
            return BackfillResult(skipped = true, success = true)
        }

        val result = runCatching { backfill() }.getOrElse { error ->
            Log.w(TAG, "Room health metrics backfill failed before completion", error)
            BackfillResult(success = false)
        }

        if (result.success) {
            appPrefs.edit()
                .putBoolean(KEY_BACKFILL_DONE, true)
                .putInt(KEY_BACKFILL_SCHEMA_VERSION, currentSchemaVersion)
                .apply()
        }

        return result
    }

    private suspend fun backfill(): BackfillResult {
        val today = todayProvider()
        val weightHistoryResult = readWeightHistory()
        val weightHistory = weightHistoryResult.getOrElse { error ->
            Log.w(TAG, "Unable to parse saved weight history during Room backfill", error)
            emptyMap()
        }

        var success = weightHistoryResult.isSuccess
        var stepDays = 0
        var sleepDays = 0
        var calorieDays = 0
        var heartPointDays = 0
        var distanceDays = 0
        var weightDays = 0

        val entitiesToUpsert = mutableMapOf<String, DailyHealthMetricEntity>()
        
        // Fetch existing metrics from DB so we can merge
        val startDateStr = today.minusDays((HealthDataManager.SYNC_HISTORY_DAYS - 1).toLong()).toString()
        val existingMetrics = roomWriter.repository.getMetricsMap(startDateStr, today.toString())

        for (offset in 0 until HealthDataManager.SYNC_HISTORY_DAYS) {
            val date = today.minusDays(offset.toLong()).toString()
            var current = existingMetrics[date] ?: DailyHealthMetricEntity(date = date)

            if (healthPrefs.contains(KEY_HIST_STEPS_PREFIX + date)) {
                val stepsSynced = healthPrefs.getBoolean(KEY_HIST_STEPS_SYNCED_PREFIX + date, false)
                current = current.copy(
                    steps = healthPrefs.getLong(KEY_HIST_STEPS_PREFIX + date, 0L),
                    stepsSynced = stepsSynced || (current.stepsSynced == true)
                )
                stepDays++
            }

            if (healthPrefs.contains(KEY_HIST_SLEEP_PREFIX + date)) {
                current = current.copy(
                    sleepHours = healthPrefs.getFloat(KEY_HIST_SLEEP_PREFIX + date, 0f).toDouble()
                )
                sleepDays++
            }

            if (healthPrefs.contains(KEY_HIST_CALS_PREFIX + date)) {
                current = current.copy(
                    totalCalories = healthPrefs.getFloat(KEY_HIST_CALS_PREFIX + date, 0f).toDouble()
                )
                calorieDays++
            }

            if (healthPrefs.contains(KEY_HIST_ACTIVE_CALS_PREFIX + date)) {
                current = current.copy(
                    activeCalories = healthPrefs.getFloat(KEY_HIST_ACTIVE_CALS_PREFIX + date, 0f).toDouble()
                )
            }

            if (healthPrefs.contains(KEY_HIST_HEART_POINTS_PREFIX + date)) {
                current = current.copy(
                    heartPoints = healthPrefs.getFloat(KEY_HIST_HEART_POINTS_PREFIX + date, 0f).toDouble()
                )
                heartPointDays++
            }

            if (healthPrefs.contains(KEY_HIST_DISTANCE_PREFIX + date)) {
                var distanceKm = try {
                    healthPrefs.getFloat(KEY_HIST_DISTANCE_PREFIX + date, 0.0f).toDouble()
                } catch (e: Exception) {
                    parseDistanceKm(healthPrefs.getString(KEY_HIST_DISTANCE_PREFIX + date, null))
                }
                
                // Normalization: If distance is suspiciously high (> 100), assume it was saved as meters
                if (distanceKm != null && distanceKm > 100.0) {
                    distanceKm /= 1000.0
                }
                
                if (distanceKm != null && distanceKm > 0.0) {
                    current = current.copy(distanceKm = distanceKm)
                    distanceDays++
                }
            }

            weightHistory[date]?.let { weightKg ->
                current = current.copy(weightKg = weightKg)
                weightDays++
            }
            
            // Only add if we actually have some data for this day
            if (current.steps != null || current.sleepHours != null || current.totalCalories != null || 
                current.activeCalories != null || current.heartPoints != null || current.distanceKm != null || 
                current.weightKg != null) {
                entitiesToUpsert[date] = current
            }
        }

        if (entitiesToUpsert.isNotEmpty()) {
            success = roomWriter.upsertAllMetrics(entitiesToUpsert.values.toList()) && success
        }

        val cutoffDate = today.minusDays((HealthDataManager.SYNC_HISTORY_DAYS - 1).toLong()).toString()
        success = roomWriter.pruneBefore(cutoffDate) && success

        Log.d(
            TAG,
            "Room health metrics batch backfill finished. success=$success, " +
                "total_entities=${entitiesToUpsert.size}, " +
                "steps=$stepDays, sleep=$sleepDays, calories=$calorieDays, " +
                "heartPoints=$heartPointDays, distance=$distanceDays, weight=$weightDays"
        )

        return BackfillResult(
            success = success,
            stepDays = stepDays,
            sleepDays = sleepDays,
            calorieDays = calorieDays,
            heartPointDays = heartPointDays,
            distanceDays = distanceDays,
            weightDays = weightDays
        )
    }

    private fun readWeightHistory(): Result<Map<String, Double>> {
        val json = healthPrefs.getString(KEY_WEIGHT_HISTORY, null) ?: return Result.success(emptyMap())
        return runCatching {
            val type = object : TypeToken<Map<String, Double>>() {}.type
            Gson().fromJson<Map<String, Double>>(json, type) ?: emptyMap()
        }
    }

    private fun parseDistanceKm(rawValue: String?): Double? {
        if (rawValue.isNullOrBlank()) return null
        return DISTANCE_NUMBER_REGEX.find(rawValue)?.value?.toDoubleOrNull()
    }

    data class BackfillResult(
        val skipped: Boolean = false,
        val success: Boolean,
        val stepDays: Int = 0,
        val sleepDays: Int = 0,
        val calorieDays: Int = 0,
        val heartPointDays: Int = 0,
        val distanceDays: Int = 0,
        val weightDays: Int = 0
    )

    private companion object {
        private const val TAG = "HealthMetricsBackfill"
        private const val PREF_HEALTH_DATA = "health_data_pref"
        private const val PREF_APP = "app_prefs"
        private const val KEY_BACKFILL_DONE = "room_health_metrics_backfill_done"
        private const val KEY_BACKFILL_SCHEMA_VERSION = "room_health_metrics_backfill_schema_v"
        private const val KEY_HIST_STEPS_PREFIX = "hist_steps_"
        private const val KEY_HIST_STEPS_SYNCED_PREFIX = "hist_steps_synced_"
        private const val KEY_HIST_SLEEP_PREFIX = "hist_sleep_"
        private const val KEY_HIST_CALS_PREFIX = "hist_cals_"
        private const val KEY_HIST_HEART_POINTS_PREFIX = "hist_heart_pts_"
        private const val KEY_HIST_DISTANCE_PREFIX = "hist_dist_"
        private const val KEY_HIST_ACTIVE_CALS_PREFIX = "hist_active_cals_"
        private const val KEY_WEIGHT_HISTORY = "weight_history_data"
        private val DISTANCE_NUMBER_REGEX = Regex("[-+]?\\d*\\.?\\d+")
    }
}
