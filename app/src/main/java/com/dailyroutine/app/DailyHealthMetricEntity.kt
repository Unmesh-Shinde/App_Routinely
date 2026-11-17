package com.dailyroutine.app

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

@Entity(tableName = "daily_health_metrics")
data class DailyHealthMetricEntity(
    @field:PrimaryKey val date: String,
    val steps: Long? = null,
    val stepsSynced: Boolean = false,
    @ColumnInfo(defaultValue = "'UNKNOWN'") val stepsSyncStatus: String = "UNKNOWN",
    val stepsSource: String? = null,
    val sleepHours: Double? = null,
    @ColumnInfo(defaultValue = "'UNKNOWN'") val sleepHoursSyncStatus: String = "UNKNOWN",
    val sleepHoursSource: String? = null,
    val activeCalories: Double? = null,
    @ColumnInfo(defaultValue = "'UNKNOWN'") val activeCaloriesSyncStatus: String = "UNKNOWN",
    val activeCaloriesSource: String? = null,
    val totalCalories: Double? = null,
    @ColumnInfo(defaultValue = "'UNKNOWN'") val totalCaloriesSyncStatus: String = "UNKNOWN",
    val totalCaloriesSource: String? = null,
    val heartPoints: Double? = null,
    @ColumnInfo(defaultValue = "'UNKNOWN'") val heartPointsSyncStatus: String = "UNKNOWN",
    val heartPointsSource: String? = null,
    val distanceKm: Double? = null,
    @ColumnInfo(defaultValue = "'UNKNOWN'") val distanceKmSyncStatus: String = "UNKNOWN",
    val distanceKmSource: String? = null,
    val weightKg: Double? = null,
    @ColumnInfo(defaultValue = "'UNKNOWN'") val weightKgSyncStatus: String = "UNKNOWN",
    val weightKgSource: String? = null,
    val updatedAtMs: Long = System.currentTimeMillis()
)


