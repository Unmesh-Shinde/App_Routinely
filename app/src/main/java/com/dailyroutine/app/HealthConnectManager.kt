package com.dailyroutine.app

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant

class HealthConnectManager(private val context: Context) {

    private val healthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(BasalMetabolicRateRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
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

    suspend fun readSteps(startTime: Instant, endTime: Instant, filterPackage: String? = null): Long {
        android.util.Log.d("DailyRoutineHealth", "readSteps: Querying from $startTime (Filter: $filterPackage)")
        val originFilter = filterPackage?.let { setOf(DataOrigin(it)) } ?: emptySet()

        // 1. Try Aggregation (Standard)
        try {
            val response = healthConnectClient.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
                    dataOriginFilter = originFilter
                )
            )
            val aggregated = response[StepsRecord.COUNT_TOTAL]
            android.util.Log.d("DailyRoutineHealth", "readSteps: Aggregated Result = $aggregated")
            if (aggregated != null) return aggregated
        } catch (e: Exception) { 
            android.util.Log.e("DailyRoutineHealth", "readSteps: Aggregation Failed", e)
        }

        // 2. Deep Fallback: Manual Scan
        return try {
            val request = ReadRecordsRequest(
                StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
                dataOriginFilter = originFilter
            )
            val response = healthConnectClient.readRecords(request)
            // Double-check origin manually to ensure no leakage from other apps
            val filteredRecords = if (filterPackage != null) {
                response.records.filter { it.metadata.dataOrigin.packageName == filterPackage }
            } else {
                response.records
            }
            val total = filteredRecords.sumOf { it.count }
            android.util.Log.d("DailyRoutineHealth", "readSteps: Raw Total = $total")
            total
        } catch (e: Exception) {
            0L
        }
    }

    suspend fun readDistanceMeters(startTime: Instant, endTime: Instant, filterPackage: String? = null): Double {
        val originFilter = filterPackage?.let { setOf(DataOrigin(it)) } ?: emptySet()
        return try {
            val response = healthConnectClient.aggregate(
                AggregateRequest(
                    metrics = setOf(DistanceRecord.DISTANCE_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
                    dataOriginFilter = originFilter
                )
            )
            response[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0.0
        } catch (e: Exception) {
            0.0
        }
    }

    suspend fun readTotalCalories(startTime: Instant, endTime: Instant, filterPackage: String? = null): Double {
        val originFilter = filterPackage?.let { setOf(DataOrigin(it)) } ?: emptySet()
        return try {
            val response = healthConnectClient.aggregate(
                AggregateRequest(
                    metrics = setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
                    dataOriginFilter = originFilter
                )
            )
            response[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories ?: 0.0
        } catch (e: Exception) {
            0.0
        }
    }

    suspend fun readMoveMinutes(startTime: Instant, endTime: Instant, filterPackage: String? = null): Int {
        val originFilter = filterPackage?.let { setOf(DataOrigin(it)) } ?: emptySet()
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
                    dataOriginFilter = originFilter
                )
            )
            val totalMins = response.records.sumOf { 
                java.time.Duration.between(it.startTime, it.endTime).toMinutes()
            }
            totalMins.toInt()
        } catch (e: Exception) {
            0
        }
    }

    suspend fun readSleepSessions(startTime: Instant, endTime: Instant, filterPackage: String? = null): List<SleepSessionRecord> {
        val originFilter = filterPackage?.let { setOf(DataOrigin(it)) } ?: emptySet()
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
                    dataOriginFilter = originFilter
                )
            )
            response.records
        } catch (e: Exception) {
            emptyList<SleepSessionRecord>()
        }
    }
}
