package com.dailyroutine.app

import android.content.Context
import java.time.LocalDate

class HealthMetricsRepository(private val dao: DailyHealthMetricDao) {

    suspend fun getMetric(date: String): DailyHealthMetricEntity? = dao.getMetric(date)

    suspend fun getMetricsBetween(startDate: String, endDate: String): List<DailyHealthMetricEntity> {
        return dao.getMetricsBetween(startDate, endDate)
    }

    suspend fun getMetricsMap(startDate: String, endDate: String): Map<String, DailyHealthMetricEntity> {
        return dao.getMetricsBetween(startDate, endDate).associateBy { it.date }
    }

    suspend fun upsertMetric(
        date: String,
        steps: Long? = null,
        stepsSynced: Boolean? = null,
        sleepHours: Double? = null,
        activeCalories: Double? = null,
        totalCalories: Double? = null,
        heartPoints: Double? = null,
        distanceKm: Double? = null,
        weightKg: Double? = null,
        source: String? = null,
        updatedAtMs: Long = System.currentTimeMillis()
    ): DailyHealthMetricEntity {
        val patch = DailyHealthMetricEntity(
            date = date,
            steps = steps,
            stepsSynced = stepsSynced ?: false,
            stepsSyncStatus = if (steps != null) "SYNCED" else "UNKNOWN",
            stepsSource = if (steps != null) source else null,
            sleepHours = sleepHours,
            sleepHoursSyncStatus = if (sleepHours != null) "SYNCED" else "UNKNOWN",
            sleepHoursSource = if (sleepHours != null) source else null,
            activeCalories = activeCalories,
            activeCaloriesSyncStatus = if (activeCalories != null) "SYNCED" else "UNKNOWN",
            activeCaloriesSource = if (activeCalories != null) source else null,
            totalCalories = totalCalories,
            totalCaloriesSyncStatus = if (totalCalories != null) "SYNCED" else "UNKNOWN",
            totalCaloriesSource = if (totalCalories != null) source else null,
            heartPoints = heartPoints,
            heartPointsSyncStatus = if (heartPoints != null) "SYNCED" else "UNKNOWN",
            heartPointsSource = if (heartPoints != null) source else null,
            distanceKm = distanceKm,
            distanceKmSyncStatus = if (distanceKm != null) "SYNCED" else "UNKNOWN",
            distanceKmSource = if (distanceKm != null) source else null,
            weightKg = weightKg,
            weightKgSyncStatus = if (weightKg != null) "SYNCED" else "UNKNOWN",
            weightKgSource = if (weightKg != null) source else null,
            updatedAtMs = updatedAtMs
        )
        return dao.mergeMetricPatch(patch)
    }

    suspend fun upsertAllMetrics(metrics: List<DailyHealthMetricEntity>) {
        metrics.forEach { dao.mergeMetricPatch(it) }
    }

    suspend fun upsertSteps(date: String, steps: Long, synced: Boolean = true): DailyHealthMetricEntity {
        return upsertMetric(date = date, steps = steps, stepsSynced = synced)
    }

    suspend fun upsertSleep(date: String, sleepHours: Double): DailyHealthMetricEntity {
        return upsertMetric(date = date, sleepHours = sleepHours)
    }

    suspend fun upsertActiveCalories(date: String, calories: Double): DailyHealthMetricEntity {
        return upsertMetric(date = date, activeCalories = calories)
    }

    suspend fun upsertTotalCalories(date: String, calories: Double): DailyHealthMetricEntity {
        return upsertMetric(date = date, totalCalories = calories)
    }

    suspend fun upsertHeartPoints(date: String, heartPoints: Double): DailyHealthMetricEntity {
        return upsertMetric(date = date, heartPoints = heartPoints)
    }

    suspend fun upsertDistance(date: String, distanceKm: Double): DailyHealthMetricEntity {
        return upsertMetric(date = date, distanceKm = distanceKm)
    }

    suspend fun upsertWeight(date: String, weightKg: Double): DailyHealthMetricEntity {
        return upsertMetric(date = date, weightKg = weightKg)
    }

    suspend fun pruneOlderThan(cutoffDate: String): Int = dao.pruneBefore(cutoffDate)

    suspend fun pruneToRetentionWindow(retentionDays: Int = HealthDataManager.SYNC_HISTORY_DAYS): Int {
        val cutoffDate = LocalDate.now().minusDays((retentionDays - 1).toLong()).toString()
        return pruneOlderThan(cutoffDate)
    }

    companion object {
        fun create(context: Context): HealthMetricsRepository {
            return HealthMetricsRepository(RoutinelyDatabase.getInstance(context).dailyHealthMetricDao())
        }
    }
}

