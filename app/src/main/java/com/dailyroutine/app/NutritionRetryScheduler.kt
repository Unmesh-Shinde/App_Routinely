package com.dailyroutine.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/** Schedules only the next persisted retry. Alarm delivery is best-effort under Android power rules. */
object NutritionRetryScheduler {
    private const val TAG = "NutritionRetryScheduler"
    private const val REQUEST_CODE = 4317

    fun schedule(context: Context) {
        val appContext = context.applicationContext
        val manager = PlanManager(appContext)
        manager.initializeLegacyNutritionRetries()
        val nextAt = manager.getPendingOvernightMeals()
            .map { it.second.nextNutritionRetryAt }
            .filter { it > 0L }
            .minOrNull()

        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = pendingIntent(appContext)
        if (nextAt == null) {
            alarmManager.cancel(pendingIntent)
            return
        }

        val triggerAt = nextAt.coerceAtLeast(System.currentTimeMillis() + 1_000L)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
        Log.d(TAG, "Scheduled the next nutrition retry wake-up.")
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, NutritionRetryAlarmReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }
}

class NutritionRetryAlarmReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        OvernightMealRetryWorker.enqueueDueWork(context)
    }
}
