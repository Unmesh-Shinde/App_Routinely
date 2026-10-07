package com.dailyroutine.app

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant

open class HealthConnectManager(private val context: Context) : IHealthConnectManager {

    private val healthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(BasalMetabolicRateRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        "android.permission.health.READ_HEALTH_DATA_HISTORY",
        "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"
    )

    override suspend fun getGrantedPermissions(): Set<String> {
        return try {
            healthConnectClient.permissionController.getGrantedPermissions()
        } catch (_: Exception) {
            emptySet()
        }
    }

    override suspend fun getMissingPermissions(): Set<String> {
        val granted = getGrantedPermissions()
        return permissions.filterNot { granted.contains(it) }.toSet()
    }

    override suspend fun readSteps(startTime: Instant, endTime: Instant, filterPackage: String?): Long {
        return readStepsOrNull(startTime, endTime, filterPackage) ?: 0L
    }

    override suspend fun readStepsOrNull(startTime: Instant, endTime: Instant, filterPackage: String?): Long? {
        val originFilter = filterPackage?.let { setOf(DataOrigin(it)) } ?: emptySet()
        return try {
            val response = healthConnectClient.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
                    dataOriginFilter = originFilter
                )
            )
            response[StepsRecord.COUNT_TOTAL] ?: 0L
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun readStepsWithFallback(startTime: Instant, endTime: Instant, filterPackage: String?): Long? {
        val filteredSteps = readStepsOrNull(startTime, endTime, filterPackage)
        if (filterPackage == null || filteredSteps == null) {
            return filteredSteps
        }

        // Some Health Connect providers write a small portion of imported/merged step records
        // under a different origin than the app selected by the user. For now, keep the
        // existing conservative selected-origin behavior, but log when the resolver would
        // safely prefer a slightly higher all-origin count. That lets us confirm the user's
        // under-count pattern before changing user-visible totals.
        val allOriginSteps = readStepsOrNull(startTime, endTime, null)
        val diagnosticResolvedSteps = StepCountResolver.resolve(
            selectedOriginSteps = filteredSteps,
            allOriginSteps = allOriginSteps,
            hasOriginFilter = true
        )
        if (diagnosticResolvedSteps != filteredSteps) {
            Log.d(
                "HealthConnectSteps",
                "Step diagnostic: package=$filterPackage selected=$filteredSteps all=$allOriginSteps candidate=$diagnosticResolvedSteps"
            )
        }

        if (filteredSteps > 0L) return filteredSteps

        // Existing fallback behavior: if the selected origin has no records, use all origins so
        // historical charts do not lose valid step history.
        return if (allOriginSteps != null && allOriginSteps > 0L) allOriginSteps else filteredSteps
    }

    override suspend fun readDistanceMeters(startTime: Instant, endTime: Instant, filterPackage: String?): Double {
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
        } catch (_: Exception) {
            0.0
        }
    }

    override suspend fun readTotalCalories(startTime: Instant, endTime: Instant, filterPackage: String?): Double {
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
        } catch (_: Exception) {
            0.0
        }
    }

    override suspend fun readActiveCalories(startTime: Instant, endTime: Instant, filterPackage: String?): Double {
        val originFilter = filterPackage?.let { setOf(DataOrigin(it)) } ?: emptySet()
        return try {
            val response = healthConnectClient.aggregate(
                AggregateRequest(
                    metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
                    dataOriginFilter = originFilter
                )
            )
            response[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0
        } catch (_: Exception) {
            0.0
        }
    }

    override suspend fun readMoveMinutes(startTime: Instant, endTime: Instant, filterPackage: String?): Int {
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
        } catch (_: Exception) {
            0
        }
    }

    override suspend fun readSleepSessions(startTime: Instant, endTime: Instant, filterPackage: String?): List<SleepSessionRecord> {
        val originFilter = filterPackage?.let { setOf(DataOrigin(it)) } ?: emptySet()
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
                    dataOriginFilter = originFilter
                )
            )
            return response.records
        } catch (_: Exception) {
            emptyList<SleepSessionRecord>()
        }
    }

    override suspend fun readWeightKg(startTime: Instant, endTime: Instant, filterPackage: String?): Double {
        val originFilter = filterPackage?.let { setOf(DataOrigin(it)) } ?: emptySet()
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
                    dataOriginFilter = originFilter
                )
            )
            response.records.maxByOrNull { it.time }?.weight?.inKilograms ?: 0.0
        } catch (_: Exception) {
            0.0
        }
    }

}
