package com.dailyroutine.app

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.TimeUnit

class HealthSyncWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): ListenableWorker.Result = withContext(Dispatchers.IO) {
        android.util.Log.d("DailyRoutineWorker", "doWork: Starting adaptive background sync...")
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

            // 🟢 DOUBLE-DAY SAFETY: Always sync TODAY and YESTERDAY
            
            // 1. Sync TODAY'S DATA
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

            // 2. Sync YESTERDAY'S DATA (Finalization/Audit)
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
            
            // Record sync time for dashboard "Proof-of-Work"
            val timestamp = SimpleDateFormat("hh:mm a, dd MMM", Locale.US).format(Date())
            hdm.setLastSyncTime(timestamp)

            // Update UI & Widget
            applicationContext.sendBroadcast(android.content.Intent("com.dailyroutine.app.DATA_UPDATED"))
            WellnessWidget.refresh(applicationContext)

            // 📅 ADAPTIVE SCHEDULING: Chain the next sync
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
            .setSmallIcon(R.drawable.ic_fitness_center)
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

        fun scheduleAutoSync(context: Context) {
            val now = Calendar.getInstance()
            val dayOfWeek = now.get(Calendar.DAY_OF_WEEK)
            val isWeekend = (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY)
            
            val currentHour = now.get(Calendar.HOUR_OF_DAY)
            
            // 😴 NIGHT MODE: 11:30 PM to 06:30 AM (Sleep deeply)
            if (currentHour >= 23 || currentHour < 6) {
                scheduleTimedSync(context, 6, 30) // Wake up at 06:30 AM
                return
            }

            // 📅 ADAPTIVE DAY MODE
            val intervalHours = if (isWeekend) 2 else 4
            
            val nextSync = (now.clone() as Calendar).apply { 
                add(Calendar.HOUR_OF_DAY, intervalHours)
            }
            
            // Safety: Ensure we don't skip 06:30 PM sleep sync if the interval jumps over it
            if (currentHour < 18 && nextSync.get(Calendar.HOUR_OF_DAY) >= 19) {
                scheduleTimedSync(context, 18, 30)
            } else {
                val delayMs = nextSync.timeInMillis - now.timeInMillis
                val request = OneTimeWorkRequestBuilder<HealthSyncWorker>()
                    .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                    .addTag(TAG_STEP_SYNC)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    "AdaptiveSync",
                    ExistingWorkPolicy.REPLACE,
                    request
                )
            }
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
