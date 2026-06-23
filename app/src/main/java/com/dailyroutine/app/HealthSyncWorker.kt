package com.dailyroutine.app

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.TimeUnit

class HealthSyncWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): ListenableWorker.Result = withContext(Dispatchers.IO) {
        android.util.Log.d("DailyRoutineWorker", "doWork: Starting background sync...")
        val hdm = HealthDataManager(applicationContext)
        if (!hdm.isConnected()) return@withContext ListenableWorker.Result.success()

        val hcm = HealthConnectManager(applicationContext)
        val appPkg = hdm.getConnectedAppPackage()
        
        val now = Instant.now()
        val startOfToday = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
        val startOfYesterday = java.time.LocalDate.now().minusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()

        try {
            val granted = hcm.getGrantedPermissions()
            val prefs = applicationContext.getSharedPreferences("health_data_pref", Context.MODE_PRIVATE)
            val editor = prefs.edit()

            // 🟢 CRITICAL: Sync both TODAY and YESTERDAY to ensure no data is lost during rollover
            
            // 1. TODAY'S SYNC
            if (granted.contains(androidx.health.connect.client.permission.HealthPermission.getReadPermission(androidx.health.connect.client.records.StepsRecord::class))) {
                val steps = hcm.readSteps(startOfToday, now, appPkg)
                editor.putString("steps_count", "%,d".format(steps))
                
                if (granted.contains(androidx.health.connect.client.permission.HealthPermission.getReadPermission(androidx.health.connect.client.records.DistanceRecord::class))) {
                    val dist = hcm.readDistanceMeters(startOfToday, now, appPkg) / 1000.0
                    editor.putString("distance_val", "%.2f km".format(dist))
                }
            }

            if (granted.contains(androidx.health.connect.client.permission.HealthPermission.getReadPermission(androidx.health.connect.client.records.SleepSessionRecord::class))) {
                val sessions = hcm.readSleepSessions(startOfToday, now, appPkg)
                val totalDurationMin = sessions.sumOf { java.time.Duration.between(it.startTime, it.endTime).toMinutes() }
                editor.putString("sleep_hours", "${totalDurationMin / 60}h ${totalDurationMin % 60}m")
            }

            if (granted.contains(androidx.health.connect.client.permission.HealthPermission.getReadPermission(androidx.health.connect.client.records.TotalCaloriesBurnedRecord::class))) {
                val burnedCals = hcm.readTotalCalories(startOfToday, now, appPkg)
                if (burnedCals > 0) editor.putString("calories_burnt", "%.0f".format(burnedCals))
            }

            // 2. YESTERDAY'S SYNC (Finalization)
            val dateYesterday = java.time.LocalDate.now().minusDays(1).toString()
            if (granted.contains(androidx.health.connect.client.permission.HealthPermission.getReadPermission(androidx.health.connect.client.records.StepsRecord::class))) {
                hdm.saveHistoricalSteps(dateYesterday, hcm.readSteps(startOfYesterday, startOfToday, appPkg))
            }
            if (granted.contains(androidx.health.connect.client.permission.HealthPermission.getReadPermission(androidx.health.connect.client.records.SleepSessionRecord::class))) {
                val sessions = hcm.readSleepSessions(startOfYesterday, startOfToday, appPkg)
                val mins = sessions.sumOf { java.time.Duration.between(it.startTime, it.endTime).toMinutes() }
                hdm.saveHistoricalSleep(dateYesterday, mins / 60.0)
            }
            if (granted.contains(androidx.health.connect.client.permission.HealthPermission.getReadPermission(androidx.health.connect.client.records.TotalCaloriesBurnedRecord::class))) {
                hdm.saveHistoricalCalories(dateYesterday, hcm.readTotalCalories(startOfYesterday, startOfToday, appPkg))
            }

            editor.apply()
            
            // UI & Widget Update
            applicationContext.sendBroadcast(android.content.Intent("com.dailyroutine.app.DATA_UPDATED"))
            WellnessWidget.refresh(applicationContext)

            android.util.Log.d("DailyRoutineWorker", "doWork: Background sync complete! ✅")

            // Reschedule if it's a timed task (recursive)
            if (tags.contains(TAG_SLEEP_SYNC)) {
                scheduleAutoSync(applicationContext) 
            }

            ListenableWorker.Result.success()
        } catch (e: Exception) {
            android.util.Log.e("DailyRoutineWorker", "doWork: Sync Failed", e)
            ListenableWorker.Result.retry()
        }
    }

    companion object {
        private const val TAG_STEP_SYNC = "step_sync_6h"
        private const val TAG_SLEEP_SYNC = "sleep_sync_scheduled"

        fun scheduleAutoSync(context: Context) {
            val workManager = WorkManager.getInstance(context)

            // A. Step Sync every 6 hours
            val stepRequest = PeriodicWorkRequestBuilder<HealthSyncWorker>(6, TimeUnit.HOURS)
                .addTag(TAG_STEP_SYNC)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()

            workManager.enqueueUniquePeriodicWork(
                "StepSync6H",
                ExistingPeriodicWorkPolicy.UPDATE,
                stepRequest
            )

            // B. Scheduled Sleep Syncs (approx logic for specific times)
            scheduleTimedSync(context, 6, 30) // 06:30 AM
            scheduleTimedSync(context, 18, 30) // 06:30 PM
        }

        private fun scheduleTimedSync(context: Context, hour: Int, min: Int) {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, min)
                set(Calendar.SECOND, 0)
            }
            if (target.before(now)) target.add(Calendar.DAY_OF_YEAR, 1)

            val delay = target.timeInMillis - now.timeInMillis
            val sleepRequest = OneTimeWorkRequestBuilder<HealthSyncWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .addTag(TAG_SLEEP_SYNC)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()

            WorkManager.getInstance(context).enqueue(sleepRequest)
        }
    }
}
