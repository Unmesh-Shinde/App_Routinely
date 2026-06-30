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
        val appPkg = hdm.getConnectedAppPackage()
        val syncMode = inputData.getString(KEY_SYNC_MODE) ?: SYNC_MODE_ALL
        val shouldSyncHistory = syncMode == SYNC_MODE_ALL || syncMode == SYNC_MODE_HISTORY
        val shouldSyncSteps = shouldSyncHistory || syncMode == SYNC_MODE_STEPS
        val shouldSyncSleep = shouldSyncHistory || syncMode == SYNC_MODE_SLEEP

        val now = Instant.now()
        val todayDate = LocalDate.now()
        val zoneId = ZoneId.systemDefault()
        val startOfToday = todayDate.atStartOfDay(zoneId).toInstant()

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
            scheduleSleepSync(context, 6, 30, "SleepSyncMorning")
            scheduleSleepSync(context, 18, 30, "SleepSyncEvening")
            scheduleHistorySync(context)
        }

        private fun scheduleNextStepSync(context: Context) {
            val now = Calendar.getInstance()
            val dayOfWeek = now.get(Calendar.DAY_OF_WEEK)
            val isWeekend = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
            val currentHour = now.get(Calendar.HOUR_OF_DAY)

            if (currentHour >= 23 || currentHour < 6) {
                scheduleStepSyncAt(context, 3, 0)
                return
            }

            val intervalHours = if (isWeekend) 2 else 4
            val nextSync = (now.clone() as Calendar).apply {
                add(Calendar.HOUR_OF_DAY, intervalHours)
            }

            val nextHour = nextSync.get(Calendar.HOUR_OF_DAY)
            val delayMs = if (nextHour >= 23 || nextHour < 6) {
                delayUntil(3, 0)
            } else {
                nextSync.timeInMillis - now.timeInMillis
            }

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

            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueName,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
