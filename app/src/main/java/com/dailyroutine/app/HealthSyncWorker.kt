package com.dailyroutine.app

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class HealthSyncWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): ListenableWorker.Result = withContext(Dispatchers.IO) {
        android.util.Log.d("DailyRoutineWorker", "doWork: Starting adaptive background sync...")
        val hdm = HealthDataManager(applicationContext)
        if (!hdm.isConnected()) return@withContext ListenableWorker.Result.success()

        val hcm = HealthConnectManager(applicationContext)
        val gfit = GoogleFitHeartPointsManager(applicationContext)
        val appPkg = hdm.getConnectedAppPackage()
        val syncMode = inputData.getString(KEY_SYNC_MODE) ?: SYNC_MODE_ALL
        val shouldSyncHistory = syncMode == SYNC_MODE_ALL || syncMode == SYNC_MODE_HISTORY
        val shouldSyncSteps = shouldSyncHistory || syncMode == SYNC_MODE_STEPS
        val shouldSyncSleep = shouldSyncHistory || syncMode == SYNC_MODE_SLEEP

        val now = Instant.now()
        val todayDate = LocalDate.now()
        val zoneId = ZoneId.systemDefault()
        val startOfToday = todayDate.atStartOfDay(zoneId).toInstant()
        var heartPointsByDate = emptyMap<String, Double>()

        try {
            val granted = hcm.getGrantedPermissions()
            val prefs = applicationContext.getSharedPreferences("health_data_pref", Context.MODE_PRIVATE)
            val editor = prefs.edit()

            if (shouldSyncSteps && granted.contains(androidx.health.connect.client.permission.HealthPermission.getReadPermission(androidx.health.connect.client.records.StepsRecord::class))) {
                val steps = hcm.readSteps(startOfToday, now, appPkg)
                editor.putString("steps_count", "%,d".format(steps))

                if (granted.contains(androidx.health.connect.client.permission.HealthPermission.getReadPermission(androidx.health.connect.client.records.DistanceRecord::class))) {
                    val dist = hcm.readDistanceMeters(startOfToday, now, appPkg) / 1000.0
                    editor.putString("distance_val", "%.2f km".format(dist))
                }
            }

            if (shouldSyncSleep && granted.contains(androidx.health.connect.client.permission.HealthPermission.getReadPermission(androidx.health.connect.client.records.SleepSessionRecord::class))) {
                val sessions = hcm.readSleepSessions(startOfToday, now, appPkg)
                val totalDurationMin = sessions.sumOf { java.time.Duration.between(it.startTime, it.endTime).toMinutes() }
                editor.putString("sleep_hours", "${totalDurationMin / 60}h ${totalDurationMin % 60}m")
            }

            if (shouldSyncSteps && granted.contains(androidx.health.connect.client.permission.HealthPermission.getReadPermission(androidx.health.connect.client.records.TotalCaloriesBurnedRecord::class))) {
                val burnedCals = hcm.readTotalCalories(startOfToday, now, appPkg)
                if (burnedCals > 0) editor.putString("calories_burnt", "%.0f".format(burnedCals))
            }
             if (shouldSyncSteps && granted.contains(androidx.health.connect.client.permission.HealthPermission.getReadPermission(androidx.health.connect.client.records.WeightRecord::class))) {
                 val weightKg = hcm.readWeightKg(startOfToday, now, appPkg)
                 if (weightKg > 0) editor.putString("current_weight", "%.1f kg".format(weightKg))
             }

            val canReadGoogleFitHeartPoints = appPkg == GoogleFitHeartPointsManager.GOOGLE_FIT_PACKAGE && gfit.hasReadPermission()

            // Heart Points are synced only from Google Fit direct API (no local calculation).
            if (canReadGoogleFitHeartPoints) {
                try {
                    val oldestDate = if (shouldSyncHistory) {
                        todayDate.minusDays((HealthDataManager.SYNC_HISTORY_DAYS - 1).toLong())
                    } else {
                        todayDate.minusDays(1)
                    }
                    heartPointsByDate = gfit.readDailyHeartPoints(oldestDate, now, zoneId)
                    val heartPoints = heartPointsByDate[todayDate.toString()] ?: gfit.readHeartPoints(startOfToday, now)
                    hdm.setHeartPoints(heartPoints.toInt())
                    android.util.Log.d("HealthSyncWorker", "Google Fit Heart Points synced for today: $heartPoints")
                } catch (e: Exception) {
                    android.util.Log.e("HealthSyncWorker", "Failed to sync Google Fit heart points: ${e.message}")
                }
            } else {
                hdm.setHeartPoints(0)
            }

            val historyRange = if (shouldSyncHistory) {
                0 until HealthDataManager.SYNC_HISTORY_DAYS
            } else {
                1..1
            }

            for (i in historyRange) {
                val date = todayDate.minusDays(i.toLong())
                val dayStart = date.atStartOfDay(zoneId).toInstant()
                val dayEnd = if (i == 0) now else date.plusDays(1).atStartOfDay(zoneId).toInstant()
                val dateKey = date.toString()

                if (shouldSyncSteps && granted.contains(androidx.health.connect.client.permission.HealthPermission.getReadPermission(androidx.health.connect.client.records.StepsRecord::class))) {
                    hdm.saveHistoricalSteps(dateKey, hcm.readSteps(dayStart, dayEnd, appPkg))
                }
                if (shouldSyncSleep && granted.contains(androidx.health.connect.client.permission.HealthPermission.getReadPermission(androidx.health.connect.client.records.SleepSessionRecord::class))) {
                    val sessions = hcm.readSleepSessions(dayStart, dayEnd, appPkg)
                    val mins = sessions.sumOf { java.time.Duration.between(it.startTime, it.endTime).toMinutes() }
                    hdm.saveHistoricalSleep(dateKey, mins / 60.0)
                }
                if (shouldSyncSteps && granted.contains(androidx.health.connect.client.permission.HealthPermission.getReadPermission(androidx.health.connect.client.records.TotalCaloriesBurnedRecord::class))) {
                    hdm.saveHistoricalCalories(dateKey, hcm.readTotalCalories(dayStart, dayEnd, appPkg))
                }
                 if (shouldSyncSteps && granted.contains(androidx.health.connect.client.permission.HealthPermission.getReadPermission(androidx.health.connect.client.records.WeightRecord::class))) {
                     val weightKg = hcm.readWeightKg(dayStart, dayEnd, appPkg)
                     if (weightKg > 0) hdm.saveWeight(dateKey, weightKg)
                 }

                if (canReadGoogleFitHeartPoints) {
                    try {
                        val pts = heartPointsByDate[dateKey] ?: 0.0
                        hdm.saveHistoricalHeartPoints(dateKey, pts)
                        if (pts > 0) {
                            android.util.Log.d("HealthSyncWorker", "Google Fit Heart Points for $dateKey: $pts")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("HealthSyncWorker", "Failed to sync Google Fit heart points for $dateKey: ${e.message}")
                    }
                } else {
                    hdm.saveHistoricalHeartPoints(dateKey, 0.0)
                }
            }
            if (shouldSyncSteps) {
                editor.putString("last_finalized_day", todayDate.toString())
            }

            editor.apply()

            val timestamp = SimpleDateFormat("hh:mm a, dd MMM", Locale.US).format(Date())
            hdm.setLastSyncTime(timestamp)

            applicationContext.sendBroadcast(android.content.Intent("com.dailyroutine.app.DATA_UPDATED"))
            WellnessWidget.refresh(applicationContext)

            scheduleAutoSync(applicationContext)

            ListenableWorker.Result.success()
        } catch (e: Exception) {
            android.util.Log.e("DailyRoutineWorker", "doWork: Sync Failed", e)
            ListenableWorker.Result.retry()
        }
    }

    private fun showSyncNotification(context: Context, msg: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channelId = "sync_channel"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(channelId, "Health Sync", android.app.NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(channel)
        }
        val notif = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Auto-Sync Active")
            .setContentText(msg)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()
        nm.notify(99, notif)
    }

    companion object {
        private const val TAG_STEP_SYNC = "step_sync_adaptive"
        private const val TAG_SLEEP_SYNC = "sleep_sync_scheduled"
        private const val TAG_HISTORY_SYNC = "history_sync_daily"
        private const val KEY_SYNC_MODE = "sync_mode"
        private const val SYNC_MODE_ALL = "all"
        private const val SYNC_MODE_STEPS = "steps"
        private const val SYNC_MODE_SLEEP = "sleep"
        private const val SYNC_MODE_HISTORY = "history"

        fun scheduleAutoSync(context: Context) {
            scheduleNextStepSync(context)
            scheduleSleepSync(context, 7, 0, "SleepSyncMorning")
            scheduleSleepSync(context, 19, 0, "SleepSyncEvening")
            scheduleHistorySync(context)
        }

        private fun scheduleNextStepSync(context: Context) {
            val now = Calendar.getInstance()
            val currentHour = now.get(Calendar.HOUR_OF_DAY)
            val currentMinute = now.get(Calendar.MINUTE)

            // Fixed Slots: 12 AM (0), 6 AM, 9 AM, 12 PM (12), 3 PM (15), 6 PM (18), 9 PM (21)
            // Note: 3 AM is skipped for StepSync because Deep History Sync runs at 3:15 AM.
            val slots = listOf(0, 6, 9, 12, 15, 18, 21)

            // Find the next slot after the current time
            val nextHour = slots.firstOrNull { it > currentHour || (it == currentHour && currentMinute < 1) } ?: 6 // Default to 6 AM (tomorrow) if all passed

            // If the next slot is 0, it means we are at 9 PM and looking for 12 AM tomorrow.
            // Our delayUntil helper handles the day-wrap automatically.

            android.util.Log.d("HealthSync", "Scheduling next StepSync. Current Hour: $currentHour. Next Slot: $nextHour")

            val delayMs = delayUntil(nextHour, 0)
            enqueueUniqueSync(context, "StepSync", TAG_STEP_SYNC, delayMs, SYNC_MODE_STEPS)
        }

        private fun scheduleStepSyncAt(context: Context, hour: Int, min: Int) {
            enqueueUniqueSync(context, "StepSync", TAG_STEP_SYNC, delayUntil(hour, min), SYNC_MODE_STEPS)
        }

        private fun scheduleSleepSync(context: Context, hour: Int, min: Int, uniqueName: String) {
            enqueueUniqueSync(context, uniqueName, TAG_SLEEP_SYNC, delayUntil(hour, min), SYNC_MODE_SLEEP)
        }

        private fun scheduleHistorySync(context: Context) {
            enqueueUniqueSync(context, "HistorySync", TAG_HISTORY_SYNC, delayUntil(3, 15), SYNC_MODE_HISTORY)
        }

        private fun delayUntil(hour: Int, min: Int): Long {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, min)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            // If the target is in the past (today), add 1 day to target tomorrow at that hour.
            if (target.before(now)) target.add(Calendar.DAY_OF_YEAR, 1)
            return target.timeInMillis - now.timeInMillis
        }

        private fun enqueueUniqueSync(context: Context, uniqueName: String, tag: String, delayMs: Long, syncMode: String) {
            val request = OneTimeWorkRequestBuilder<HealthSyncWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_SYNC_MODE to syncMode))
                .addTag(tag)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()

            // policy = KEEP: Ensures that if a sync is already scheduled for a fixed hour,
            // opening the app (which calls scheduleAutoSync) does not cancel it and restart the delay.
            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueName,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
