package com.dailyroutine.app

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DailyHealthMetricDao {
    @Query("SELECT * FROM daily_health_metrics WHERE date = :date LIMIT 1")
    suspend fun getMetric(date: String): DailyHealthMetricEntity?

    @Query("SELECT * FROM daily_health_metrics WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    suspend fun getMetricsBetween(startDate: String, endDate: String): List<DailyHealthMetricEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(metric: DailyHealthMetricEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(metrics: List<DailyHealthMetricEntity>)

    @Query("DELETE FROM daily_health_metrics WHERE date < :cutoffDate")
    suspend fun pruneBefore(cutoffDate: String): Int

    @Query("DELETE FROM daily_health_metrics")
    suspend fun deleteAll()
}

