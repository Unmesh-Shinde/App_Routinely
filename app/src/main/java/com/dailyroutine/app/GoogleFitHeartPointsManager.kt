package com.dailyroutine.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.Bucket
import com.google.android.gms.fitness.data.DataPoint
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field
import com.google.android.gms.fitness.request.DataReadRequest
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class GoogleFitHeartPointsManager(private val context: Context) {

    companion object {
        const val GOOGLE_FIT_PACKAGE = "com.google.android.apps.fitness"
        const val HEART_POINTS_REQUEST_CODE = 9001
    }

    sealed class PermissionResult {
        data class Granted(val email: String?) : PermissionResult()
        data class MissingFitnessScope(val email: String?) : PermissionResult()
        data class Failed(val statusCode: Int, val message: String?) : PermissionResult()
    }

    private val fitnessOptions: FitnessOptions by lazy {
        FitnessOptions.builder()
            .addDataType(DataType.TYPE_HEART_POINTS, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.AGGREGATE_HEART_POINTS, FitnessOptions.ACCESS_READ)
            .build()
    }

    fun hasReadPermission(activity: Activity): Boolean {
        return signedInOrExtensionAccount(activity)?.let { account ->
            GoogleSignIn.hasPermissions(account, fitnessOptions)
        } ?: false
    }

    fun hasReadPermission(): Boolean {
        return signedInOrExtensionAccount(context)?.let { account ->
            GoogleSignIn.hasPermissions(account, fitnessOptions)
        } ?: false
    }

    fun requestReadPermission(activity: Activity) {
        val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .addExtension(fitnessOptions)
            .build()
        activity.startActivityForResult(
            GoogleSignIn.getClient(activity, signInOptions).signInIntent,
            HEART_POINTS_REQUEST_CODE
        )
    }

    fun handlePermissionResult(data: Intent?): PermissionResult {
        return try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException::class.java)
            if (account != null && GoogleSignIn.hasPermissions(account, fitnessOptions)) {
                PermissionResult.Granted(account.email)
            } else {
                PermissionResult.MissingFitnessScope(account?.email)
            }
        } catch (e: ApiException) {
            Log.e("GoogleFitHeartPoints", "Google Fit sign-in failed. status=${e.statusCode} message=${e.message}", e)
            PermissionResult.Failed(e.statusCode, e.message)
        } catch (e: Exception) {
            Log.e("GoogleFitHeartPoints", "Google Fit sign-in failed", e)
            PermissionResult.Failed(-1, e.message)
        }
    }

    fun signedInEmail(): String? = signedInOrExtensionAccount(context)?.email

    suspend fun readHeartPoints(startTime: Instant, endTime: Instant): Double {
        return withContext(Dispatchers.IO) {
            try {
                val account = signedInOrExtensionAccount(context)
                if (account == null || !GoogleSignIn.hasPermissions(account, fitnessOptions)) {
                    Log.w("GoogleFitHeartPoints", "Google Fit Heart Points permission not granted")
                    return@withContext 0.0
                }

                val request = DataReadRequest.Builder()
                    .read(DataType.TYPE_HEART_POINTS)
                    .setTimeRange(startTime.toEpochMilli(), endTime.toEpochMilli(), TimeUnit.MILLISECONDS)
                    .build()

                val response = Tasks.await(Fitness.getHistoryClient(context, account).readData(request))
                var total = response.dataSets.sumOf { dataSet ->
                    dataSet.dataPoints.sumOf { dataPoint -> pointsFromRawDataPoint(dataPoint) }
                }

                if (total == 0.0) {
                    total = readAggregateHeartPoints(startTime, endTime)
                }

                Log.d("GoogleFitHeartPoints", "Fetched Google Fit Heart Points: $total")
                total
            } catch (e: Exception) {
                Log.e("GoogleFitHeartPoints", "Failed to fetch Google Fit Heart Points", e)
                0.0
            }
        }
    }

    suspend fun readDailyHeartPoints(
        oldestDate: LocalDate,
        endTime: Instant,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Map<String, Double> {
        return withContext(Dispatchers.IO) {
            try {
                val account = signedInOrExtensionAccount(context)
                if (account == null || !GoogleSignIn.hasPermissions(account, fitnessOptions)) {
                    Log.w("GoogleFitHeartPoints", "Google Fit Heart Points permission not granted for daily read")
                    return@withContext emptyMap()
                }

                val startTime = oldestDate.atStartOfDay(zoneId).toInstant()
                val request = DataReadRequest.Builder()
                    .read(DataType.TYPE_HEART_POINTS)
                    .setTimeRange(startTime.toEpochMilli(), endTime.toEpochMilli(), TimeUnit.MILLISECONDS)
                    .build()

                val response = Tasks.await(Fitness.getHistoryClient(context, account).readData(request))
                val values = mutableMapOf<String, Double>()

                response.dataSets.forEach { dataSet ->
                    dataSet.dataPoints.forEach { dataPoint ->
                        addRawPointToDailyValues(values, dataPoint, zoneId)
                    }
                }

                if (values.isEmpty() || values.values.all { it == 0.0 }) {
                    return@withContext readDailyAggregateHeartPoints(oldestDate, endTime, zoneId)
                }

                Log.d("GoogleFitHeartPoints", "Fetched daily Google Fit Heart Points for ${values.size} buckets")
                values
            } catch (e: Exception) {
                Log.e("GoogleFitHeartPoints", "Failed to fetch daily Google Fit Heart Points", e)
                emptyMap()
            }
        }
    }

    private fun readAggregateHeartPoints(startTime: Instant, endTime: Instant): Double {
        val account = signedInOrExtensionAccount(context) ?: return 0.0
        val request = DataReadRequest.Builder()
            .aggregate(DataType.TYPE_HEART_POINTS, DataType.AGGREGATE_HEART_POINTS)
            .setTimeRange(startTime.toEpochMilli(), endTime.toEpochMilli(), TimeUnit.MILLISECONDS)
            .build()

        val response = Tasks.await(Fitness.getHistoryClient(context, account).readData(request))
        return response.dataSets.sumOf { dataSet ->
            dataSet.dataPoints.sumOf { dataPoint -> pointsFromAggregateDataPoint(dataPoint) }
        }
    }

    private fun readDailyAggregateHeartPoints(
        oldestDate: LocalDate,
        endTime: Instant,
        zoneId: ZoneId
    ): Map<String, Double> {
        val account = signedInOrExtensionAccount(context) ?: return emptyMap()
        val startTime = oldestDate.atStartOfDay(zoneId).toInstant()
        val request = DataReadRequest.Builder()
            .aggregate(DataType.TYPE_HEART_POINTS, DataType.AGGREGATE_HEART_POINTS)
            .setTimeRange(startTime.toEpochMilli(), endTime.toEpochMilli(), TimeUnit.MILLISECONDS)
            .bucketByTime(1, TimeUnit.DAYS)
            .build()

        val response = Tasks.await(Fitness.getHistoryClient(context, account).readData(request))
        val values = mutableMapOf<String, Double>()
        response.buckets.forEach { bucket ->
            val key = bucketDateKey(bucket, zoneId)
            val points = bucket.dataSets.sumOf { dataSet ->
                dataSet.dataPoints.sumOf { dataPoint -> pointsFromAggregateDataPoint(dataPoint) }
            }
            values[key] = (values[key] ?: 0.0) + points
        }
        Log.d("GoogleFitHeartPoints", "Fetched daily aggregate Google Fit Heart Points for ${values.size} buckets")
        return values
    }

    private fun bucketDateKey(bucket: Bucket, zoneId: ZoneId): String {
        return Instant.ofEpochMilli(bucket.getStartTime(TimeUnit.MILLISECONDS))
            .atZone(zoneId)
            .toLocalDate()
            .format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    private fun addRawPointToDailyValues(
        values: MutableMap<String, Double>,
        dataPoint: DataPoint,
        zoneId: ZoneId
    ) {
        val points = pointsFromRawDataPoint(dataPoint)
        if (points <= 0.0) return

        val start = dataPoint.getStartTime(TimeUnit.MILLISECONDS)
        val end = dataPoint.getEndTime(TimeUnit.MILLISECONDS)
        if (end <= start) {
            val key = Instant.ofEpochMilli(start).atZone(zoneId).toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
            values[key] = (values[key] ?: 0.0) + points
            return
        }

        var cursor = start
        val totalDuration = (end - start).toDouble()
        while (cursor < end) {
            val date = Instant.ofEpochMilli(cursor).atZone(zoneId).toLocalDate()
            val nextDayStart = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            val segmentEnd = minOf(end, nextDayStart)
            val fraction = (segmentEnd - cursor) / totalDuration
            val key = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            values[key] = (values[key] ?: 0.0) + (points * fraction)
            cursor = segmentEnd
        }
    }

    private fun pointsFromRawDataPoint(dataPoint: DataPoint): Double {
        val intensity = numericValue(dataPoint, Field.FIELD_INTENSITY)
        val durationMinutes = dataPointDurationMinutes(dataPoint)
        val points = if (durationMinutes > 0.0 && intensity in 0.0..2.5) {
            intensity * durationMinutes
        } else {
            intensity
        }
        logHeartPointDataPoint("raw", dataPoint, intensity, durationMinutes, points)
        return points.coerceAtLeast(0.0)
    }

    private fun pointsFromAggregateDataPoint(dataPoint: DataPoint): Double {
        val intensity = numericValue(dataPoint, Field.FIELD_INTENSITY)
        val durationMinutes = numericValue(dataPoint, Field.FIELD_DURATION) / 60_000.0
        val points = if (durationMinutes > 0.0 && intensity in 0.0..2.5) {
            intensity * durationMinutes
        } else {
            intensity
        }
        logHeartPointDataPoint("aggregate", dataPoint, intensity, durationMinutes, points)
        return points.coerceAtLeast(0.0)
    }

    private fun dataPointDurationMinutes(dataPoint: DataPoint): Double {
        val start = dataPoint.getStartTime(TimeUnit.MILLISECONDS)
        val end = dataPoint.getEndTime(TimeUnit.MILLISECONDS)
        return ((end - start).coerceAtLeast(0L)) / 60_000.0
    }

    private fun numericValue(dataPoint: DataPoint, field: Field): Double {
        if (!dataPoint.dataType.fields.contains(field)) return 0.0
        val value = dataPoint.getValue(field)
        return runCatching { value.asFloat().toDouble() }
            .getOrElse { runCatching { value.asInt().toDouble() }.getOrDefault(0.0) }
    }

    private fun logHeartPointDataPoint(
        source: String,
        dataPoint: DataPoint,
        intensity: Double,
        durationMinutes: Double,
        points: Double
    ) {
        Log.d(
            "GoogleFitHeartPoints",
            "$source point type=${dataPoint.dataType.name} start=${dataPoint.getStartTime(TimeUnit.MILLISECONDS)} end=${dataPoint.getEndTime(TimeUnit.MILLISECONDS)} intensity=$intensity durationMin=$durationMinutes points=$points"
        )
    }

    private fun signedInOrExtensionAccount(context: Context): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
            ?: GoogleSignIn.getAccountForExtension(context, fitnessOptions)
    }
}
