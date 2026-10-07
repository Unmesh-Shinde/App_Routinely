package com.dailyroutine.app

import androidx.health.connect.client.records.SleepSessionRecord
import java.time.Instant

interface IHealthConnectManager {
    suspend fun getGrantedPermissions(): Set<String>
    suspend fun getMissingPermissions(): Set<String>
    suspend fun readSteps(startTime: Instant, endTime: Instant, filterPackage: String? = null): Long
    suspend fun readStepsOrNull(startTime: Instant, endTime: Instant, filterPackage: String? = null): Long?
    suspend fun readStepsWithFallback(startTime: Instant, endTime: Instant, filterPackage: String? = null): Long?
    suspend fun readDistanceMeters(startTime: Instant, endTime: Instant, filterPackage: String? = null): Double
    suspend fun readTotalCalories(startTime: Instant, endTime: Instant, filterPackage: String? = null): Double
    suspend fun readActiveCalories(startTime: Instant, endTime: Instant, filterPackage: String? = null): Double
    suspend fun readMoveMinutes(startTime: Instant, endTime: Instant, filterPackage: String? = null): Int
    suspend fun readSleepSessions(startTime: Instant, endTime: Instant, filterPackage: String? = null): List<SleepSessionRecord>
    suspend fun readWeightKg(startTime: Instant, endTime: Instant, filterPackage: String? = null): Double
}
