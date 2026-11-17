package com.dailyroutine.app

import androidx.health.connect.client.records.SleepSessionRecord
import java.time.Instant

interface IHealthConnectManager {
    suspend fun getGrantedPermissions(): Set<String>
    /** Null means the permission query failed; an empty set is a successful query. */
    suspend fun getGrantedPermissionsOrNull(): Set<String>? = getGrantedPermissions()
    suspend fun getMissingPermissions(): Set<String>
    suspend fun readSteps(startTime: Instant, endTime: Instant): Long
    suspend fun readStepsOrNull(startTime: Instant, endTime: Instant): Long?
    suspend fun readStepsWithFallback(startTime: Instant, endTime: Instant): Long?
    suspend fun readDistanceMeters(startTime: Instant, endTime: Instant): Double
    suspend fun readDistanceMetersOrNull(startTime: Instant, endTime: Instant): Double? = readDistanceMeters(startTime, endTime)
    suspend fun readTotalCalories(startTime: Instant, endTime: Instant): Double
    suspend fun readTotalCaloriesOrNull(startTime: Instant, endTime: Instant): Double? = readTotalCalories(startTime, endTime)
    suspend fun readActiveCalories(startTime: Instant, endTime: Instant): Double
    suspend fun readActiveCaloriesOrNull(startTime: Instant, endTime: Instant): Double? = readActiveCalories(startTime, endTime)
    suspend fun readMoveMinutes(startTime: Instant, endTime: Instant): Int
    suspend fun readSleepSessions(startTime: Instant, endTime: Instant): List<SleepSessionRecord>
    /** Null means the query failed; an empty list is an authoritative no-sessions result. */
    suspend fun readSleepSessionsOrNull(startTime: Instant, endTime: Instant): List<SleepSessionRecord>? = readSleepSessions(startTime, endTime)
    suspend fun readWalkingDurationMinutesOrNull(startTime: Instant, endTime: Instant): Long?
}
