package com.dailyroutine.app

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_health_metrics")
data class DailyHealthMetricEntity(
    @field:PrimaryKey val date: String,
    val steps: Long? = null,
    val stepsSynced: Boolean = false,
    val sleepHours: Double? = null,
    val activeCalories: Double? = null,
    val totalCalories: Double? = null,
    val heartPoints: Double? = null,
    val distanceKm: Double? = null,
    val weightKg: Double? = null,
    val updatedAtMs: Long = System.currentTimeMillis()
)


