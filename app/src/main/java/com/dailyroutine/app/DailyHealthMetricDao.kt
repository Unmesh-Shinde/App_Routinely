package com.dailyroutine.app

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface DailyHealthMetricDao {
    @Query("SELECT * FROM daily_health_metrics WHERE date = :date LIMIT 1")
    suspend fun getMetric(date: String): DailyHealthMetricEntity?

    @Query("SELECT * FROM daily_health_metrics WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    suspend fun getMetricsBetween(startDate: String, endDate: String): List<DailyHealthMetricEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(metric: DailyHealthMetricEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(metric: DailyHealthMetricEntity)

    @Query("""
        UPDATE daily_health_metrics SET
            steps = COALESCE(:steps, steps),
            stepsSynced = CASE WHEN :steps IS NULL THEN stepsSynced ELSE :stepsSynced END,
            stepsSyncStatus = CASE WHEN :steps IS NULL THEN stepsSyncStatus ELSE :stepsSyncStatus END,
            stepsSource = CASE WHEN :steps IS NULL THEN stepsSource ELSE COALESCE(:stepsSource, stepsSource) END,
            sleepHours = COALESCE(:sleepHours, sleepHours),
            sleepHoursSyncStatus = CASE WHEN :sleepHours IS NULL THEN sleepHoursSyncStatus ELSE :sleepHoursSyncStatus END,
            sleepHoursSource = CASE WHEN :sleepHours IS NULL THEN sleepHoursSource ELSE COALESCE(:sleepHoursSource, sleepHoursSource) END,
            activeCalories = COALESCE(:activeCalories, activeCalories),
            activeCaloriesSyncStatus = CASE WHEN :activeCalories IS NULL THEN activeCaloriesSyncStatus ELSE :activeCaloriesSyncStatus END,
            activeCaloriesSource = CASE WHEN :activeCalories IS NULL THEN activeCaloriesSource ELSE COALESCE(:activeCaloriesSource, activeCaloriesSource) END,
            totalCalories = COALESCE(:totalCalories, totalCalories),
            totalCaloriesSyncStatus = CASE WHEN :totalCalories IS NULL THEN totalCaloriesSyncStatus ELSE :totalCaloriesSyncStatus END,
            totalCaloriesSource = CASE WHEN :totalCalories IS NULL THEN totalCaloriesSource ELSE COALESCE(:totalCaloriesSource, totalCaloriesSource) END,
            heartPoints = COALESCE(:heartPoints, heartPoints),
            heartPointsSyncStatus = CASE WHEN :heartPoints IS NULL THEN heartPointsSyncStatus ELSE :heartPointsSyncStatus END,
            heartPointsSource = CASE WHEN :heartPoints IS NULL THEN heartPointsSource ELSE COALESCE(:heartPointsSource, heartPointsSource) END,
            distanceKm = COALESCE(:distanceKm, distanceKm),
            distanceKmSyncStatus = CASE WHEN :distanceKm IS NULL THEN distanceKmSyncStatus ELSE :distanceKmSyncStatus END,
            distanceKmSource = CASE WHEN :distanceKm IS NULL THEN distanceKmSource ELSE COALESCE(:distanceKmSource, distanceKmSource) END,
            weightKg = COALESCE(:weightKg, weightKg),
            weightKgSyncStatus = CASE WHEN :weightKg IS NULL THEN weightKgSyncStatus ELSE :weightKgSyncStatus END,
            weightKgSource = CASE WHEN :weightKg IS NULL THEN weightKgSource ELSE COALESCE(:weightKgSource, weightKgSource) END,
            updatedAtMs = :updatedAtMs
        WHERE date = :date
    """)
    suspend fun applyMetricPatch(
        date: String,
        steps: Long?,
        stepsSynced: Boolean,
        stepsSyncStatus: String,
        stepsSource: String?,
        sleepHours: Double?,
        sleepHoursSyncStatus: String,
        sleepHoursSource: String?,
        activeCalories: Double?,
        activeCaloriesSyncStatus: String,
        activeCaloriesSource: String?,
        totalCalories: Double?,
        totalCaloriesSyncStatus: String,
        totalCaloriesSource: String?,
        heartPoints: Double?,
        heartPointsSyncStatus: String,
        heartPointsSource: String?,
        distanceKm: Double?,
        distanceKmSyncStatus: String,
        distanceKmSource: String?,
        weightKg: Double?,
        weightKgSyncStatus: String,
        weightKgSource: String?,
        updatedAtMs: Long
    )

    @Transaction
    suspend fun mergeMetricPatch(patch: DailyHealthMetricEntity): DailyHealthMetricEntity {
        insertIfAbsent(DailyHealthMetricEntity(date = patch.date))
        applyMetricPatch(
            date = patch.date,
            steps = patch.steps,
            stepsSynced = patch.stepsSynced,
            stepsSyncStatus = patch.stepsSyncStatus,
            stepsSource = patch.stepsSource,
            sleepHours = patch.sleepHours,
            sleepHoursSyncStatus = patch.sleepHoursSyncStatus,
            sleepHoursSource = patch.sleepHoursSource,
            activeCalories = patch.activeCalories,
            activeCaloriesSyncStatus = patch.activeCaloriesSyncStatus,
            activeCaloriesSource = patch.activeCaloriesSource,
            totalCalories = patch.totalCalories,
            totalCaloriesSyncStatus = patch.totalCaloriesSyncStatus,
            totalCaloriesSource = patch.totalCaloriesSource,
            heartPoints = patch.heartPoints,
            heartPointsSyncStatus = patch.heartPointsSyncStatus,
            heartPointsSource = patch.heartPointsSource,
            distanceKm = patch.distanceKm,
            distanceKmSyncStatus = patch.distanceKmSyncStatus,
            distanceKmSource = patch.distanceKmSource,
            weightKg = patch.weightKg,
            weightKgSyncStatus = patch.weightKgSyncStatus,
            weightKgSource = patch.weightKgSource,
            updatedAtMs = patch.updatedAtMs
        )
        return getMetric(patch.date) ?: error("Metric row disappeared during merge")
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(metrics: List<DailyHealthMetricEntity>)

    @Query("DELETE FROM daily_health_metrics WHERE date < :cutoffDate")
    suspend fun pruneBefore(cutoffDate: String): Int

    @Query("DELETE FROM daily_health_metrics")
    suspend fun deleteAll()
}

