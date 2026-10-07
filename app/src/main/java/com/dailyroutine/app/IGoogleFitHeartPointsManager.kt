package com.dailyroutine.app

import android.content.Intent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

interface IGoogleFitHeartPointsManager {
    fun hasReadPermission(activity: android.app.Activity): Boolean
    fun hasReadPermission(): Boolean
    fun requestReadPermission(activity: android.app.Activity)
    fun handlePermissionResult(data: Intent?): GoogleFitHeartPointsManager.PermissionResult
    fun signedInEmail(): String?
    suspend fun readHeartPoints(startTime: Instant, endTime: Instant): Double
    suspend fun readDailyHeartPoints(startDate: LocalDate, endTime: Instant, zoneId: ZoneId): Map<String, Double>
}
