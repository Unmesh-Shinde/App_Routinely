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
        updatedAtMs: Long = System.currentTimeMillis()
    ): DailyHealthMetricEntity {
        val existing = dao.getMetric(date)
        val merged = mergeMetric(existing, date, steps, stepsSynced, sleepHours, activeCalories, totalCalories, heartPoints, distanceKm, weightKg, updatedAtMs)
        dao.upsert(merged)
        return merged
    }

    suspend fun upsertAllMetrics(metrics: List<DailyHealthMetricEntity>) {
        dao.upsertAll(metrics)
    }

    private fun mergeMetric(
        existing: DailyHealthMetricEntity?,
        date: String,
        steps: Long? = null,
        stepsSynced: Boolean? = null,
        sleepHours: Double? = null,
        activeCalories: Double? = null,
        totalCalories: Double? = null,
        heartPoints: Double? = null,
        distanceKm: Double? = null,
        weightKg: Double? = null,
        updatedAtMs: Long = System.currentTimeMillis()
    ): DailyHealthMetricEntity {
        return DailyHealthMetricEntity(
            date = date,
            steps = steps ?: existing?.steps,
            stepsSynced = stepsSynced ?: existing?.stepsSynced ?: false,
            sleepHours = sleepHours ?: existing?.sleepHours,
            activeCalories = activeCalories ?: existing?.activeCalories,
            totalCalories = totalCalories ?: existing?.totalCalories,
            heartPoints = heartPoints ?: existing?.heartPoints,
            distanceKm = distanceKm ?: existing?.distanceKm,
            weightKg = weightKg ?: existing?.weightKg,
            updatedAtMs = updatedAtMs
        )
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

