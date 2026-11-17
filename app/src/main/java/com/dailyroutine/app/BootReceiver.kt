package com.dailyroutine.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val validActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON"
        )
        if (intent.action in validActions) {
            val clockChanged = intent.action != Intent.ACTION_BOOT_COMPLETED &&
                intent.action != "android.intent.action.QUICKBOOT_POWERON" &&
                intent.action != "com.htc.intent.action.QUICKBOOT_POWERON"
            ReminderManager(context).scheduleAllEnabled()
            HealthSyncWorker.scheduleAutoSync(context, rescheduleForClockChange = clockChanged)
            WaterIntakeSummaryWorker.scheduleNext(context)
            NutritionRetryScheduler.schedule(context)
        }
    }
}
