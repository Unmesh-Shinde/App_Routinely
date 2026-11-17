package com.dailyroutine.app

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
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
        "android.permission.health.READ_HEALTH_DATA_HISTORY",
        "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"
    )

    override suspend fun getGrantedPermissions(): Set<String> {
        return getGrantedPermissionsOrNull() ?: emptySet()
    }

    override suspend fun getGrantedPermissionsOrNull(): Set<String>? {
        return try { healthConnectClient.permissionController.getGrantedPermissions() }
        catch (e: Exception) { Log.w("HealthConnectManager", "Permission query failed", e); null }
    }

    override suspend fun getMissingPermissions(): Set<String> {
        val granted = getGrantedPermissions()
        return permissions.filterNot { granted.contains(it) }.toSet()
    }

    override suspend fun readSteps(startTime: Instant, endTime: Instant): Long {
        return readStepsOrNull(startTime, endTime) ?: 0L
    }

    override suspend fun readStepsOrNull(startTime: Instant, endTime: Instant): Long? {
        return try {
            val response = healthConnectClient.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            response[StepsRecord.COUNT_TOTAL] ?: 0L
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun readStepsWithFallback(startTime: Instant, endTime: Instant): Long? {
        // No origin filter: Health Connect supplies its deduplicated total across sources.
        return readStepsOrNull(startTime, endTime)
    }

    override suspend fun readDistanceMeters(startTime: Instant, endTime: Instant): Double {
        return readDistanceMetersOrNull(startTime, endTime) ?: 0.0
    }

    override suspend fun readDistanceMetersOrNull(startTime: Instant, endTime: Instant): Double? {
        return try {
            val response = healthConnectClient.aggregate(
                AggregateRequest(
                    metrics = setOf(DistanceRecord.DISTANCE_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            response[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0.0
        } catch (e: Exception) {
            Log.w("HealthConnectManager", "Distance read failed", e)
            null
        }
    }

    override suspend fun readTotalCalories(startTime: Instant, endTime: Instant): Double {
        return readTotalCaloriesOrNull(startTime, endTime) ?: 0.0
    }

    override suspend fun readTotalCaloriesOrNull(startTime: Instant, endTime: Instant): Double? {
        return try {
            val response = healthConnectClient.aggregate(
                AggregateRequest(
                    metrics = setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            response[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories ?: 0.0
        } catch (e: Exception) {
            Log.w("HealthConnectManager", "Total calorie read failed", e)
            null
        }
    }

    override suspend fun readActiveCalories(startTime: Instant, endTime: Instant): Double {
        return readActiveCaloriesOrNull(startTime, endTime) ?: 0.0
    }

    override suspend fun readActiveCaloriesOrNull(startTime: Instant, endTime: Instant): Double? {
        return try {
            val response = healthConnectClient.aggregate(
                AggregateRequest(
                    metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            response[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0
        } catch (e: Exception) {
            Log.w("HealthConnectManager", "Active calorie read failed", e)
            null
        }
    }

    override suspend fun readMoveMinutes(startTime: Instant, endTime: Instant): Int {
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
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

    override suspend fun readWalkingDurationMinutesOrNull(startTime: Instant, endTime: Instant): Long? {
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            val intervals = response.records
                .asSequence()
                .filter { it.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_WALKING }
                .mapNotNull { session ->
                    val clippedStart = maxOf(session.startTime, startTime)
                    val clippedEnd = minOf(session.endTime, endTime)
                    if (clippedEnd.isAfter(clippedStart)) clippedStart to clippedEnd else null
                }
                .sortedBy { it.first }
                .toList()

            var mergedStart: Instant? = null
            var mergedEnd: Instant? = null
            var totalMillis = 0L
            for ((start, end) in intervals) {
                val currentEnd = mergedEnd
                if (currentEnd == null || start.isAfter(currentEnd)) {
                    if (mergedStart != null && currentEnd != null) {
                        totalMillis += java.time.Duration.between(mergedStart, currentEnd).toMillis()
                    }
                    mergedStart = start
                    mergedEnd = end
                } else if (end.isAfter(currentEnd)) {
                    mergedEnd = end
                }
            }
            if (mergedStart != null && mergedEnd != null) {
                totalMillis += java.time.Duration.between(mergedStart, mergedEnd).toMillis()
            }
            java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(totalMillis)
        } catch (e: Exception) {
            Log.w("HealthConnectManager", "Walking duration read failed", e)
            null
        }
    }

    override suspend fun readSleepSessions(startTime: Instant, endTime: Instant): List<SleepSessionRecord> {
        return readSleepSessionsOrNull(startTime, endTime).orEmpty()
    }

    override suspend fun readSleepSessionsOrNull(startTime: Instant, endTime: Instant): List<SleepSessionRecord>? {
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            return response.records
        } catch (e: Exception) {
            Log.w("HealthConnectManager", "Sleep session read failed", e)
            null
        }
    }

}
