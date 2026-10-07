package com.dailyroutine.app

import android.content.Context
import android.util.Log

class HealthMetricsRoomWriter(internal val repository: HealthMetricsRepository) {

    constructor(context: Context) : this(HealthMetricsRepository.create(context.applicationContext))

    suspend fun upsertMetric(
        date: String,
        steps: Long? = null,
        stepsSynced: Boolean? = null,
        sleepHours: Double? = null,
        activeCalories: Double? = null,
        totalCalories: Double? = null,
        heartPoints: Double? = null,
        distanceKm: Double? = null,
        weightKg: Double? = null
    ): Boolean {
        return safeWrite("metric", date) {
            repository.upsertMetric(
                date = date,
                steps = steps,
                stepsSynced = stepsSynced,
                sleepHours = sleepHours,
                activeCalories = activeCalories,
                totalCalories = totalCalories,
                heartPoints = heartPoints,
                distanceKm = distanceKm,
                weightKg = weightKg
            )
        }
    }

    suspend fun upsertAllMetrics(metrics: List<DailyHealthMetricEntity>): Boolean {
        return safeWrite("batch_metrics", "size=${metrics.size}") {
            repository.upsertAllMetrics(metrics)
        }
    }

    suspend fun pruneToRetentionWindow(retentionDays: Int = HealthDataManager.SYNC_HISTORY_DAYS): Boolean {
        return safeWrite("prune", "retention=$retentionDays") {
            repository.pruneToRetentionWindow(retentionDays)
        }
    }

    suspend fun pruneBefore(cutoffDate: String): Boolean {
        return safeWrite("prune", "cutoff=$cutoffDate") {
            repository.pruneOlderThan(cutoffDate)
        }
    }

    private suspend fun safeWrite(label: String, date: String, block: suspend () -> Unit): Boolean {
        return try {
            block()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Room dual-write failed for $label on $date; existing prefs write remains authoritative", e)
            false
        }
    }

    private companion object {
        private const val TAG = "HealthMetricsRoomWriter"
    }
}
