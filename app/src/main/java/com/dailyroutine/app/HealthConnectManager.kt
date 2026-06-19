package com.dailyroutine.app

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.request.AggregateRequest
import java.time.Instant

class HealthConnectManager(private val context: Context) {

    private val healthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        "android.permission.health.READ_HEALTH_DATA_HISTORY",
        "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"
    )

    suspend fun getGrantedPermissions(): Set<String> {
        return try {
            healthConnectClient.permissionController.getGrantedPermissions()
        } catch (e: Exception) {
            emptySet()
        }
    }

    suspend fun hasPermission(permission: String): Boolean {
        return getGrantedPermissions().contains(permission)
    }

    suspend fun hasAnyPermission(): Boolean {
        val granted = getGrantedPermissions()
        for (p in permissions) {
            if (granted.contains(p)) return true
        }
        return false
    }

    suspend fun readSteps(startTime: Instant, endTime: Instant): Long {
        android.util.Log.d("DailyRoutineHealth", "readSteps: Querying from $startTime")
        // 1. Try Aggregation (Official way)
        try {
            val response = healthConnectClient.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            val aggregated = response[StepsRecord.COUNT_TOTAL]
            android.util.Log.d("DailyRoutineHealth", "readSteps: Aggregated Result = $aggregated")
            if (aggregated != null && aggregated > 0) return aggregated
        } catch (e: Exception) { 
            android.util.Log.e("DailyRoutineHealth", "readSteps: Aggregation Failed", e)
        }

        // 2. Deep Fallback: Manual Scan of Raw Records
        android.util.Log.d("DailyRoutineHealth", "readSteps: Falling back to Raw Record Scan...")
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            android.util.Log.d("DailyRoutineHealth", "readSteps: Raw Records Found = ${response.records.size}")
            
            // Sum all steps found in this period across all devices
            val total = response.records.sumOf { it.count }
            android.util.Log.d("DailyRoutineHealth", "readSteps: Raw Total = $total")
            total
        } catch (e: Exception) {
            android.util.Log.e("DailyRoutineHealth", "readSteps: Raw Scan Failed", e)
            0L
        }
    }

    suspend fun readCalories(startTime: Instant, endTime: Instant): Double {
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    ActiveCaloriesBurnedRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            response.records.sumOf { it.energy.inKilocalories }
        } catch (e: Exception) {
            0.0
        }
    }

    suspend fun readSleepSessions(startTime: Instant, endTime: Instant): List<SleepSessionRecord> {
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            response.records
        } catch (e: Exception) {
            emptyList()
        }
    }
}
