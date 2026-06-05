package com.dailyroutine.app

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getIntExtra(ReminderManager.EXTRA_ID, -1)
        if (reminderId < 0) return

        val manager = ReminderManager(context)
        val reminder = manager.getReminderById(reminderId) ?: Reminder(
            id = reminderId,
            title = intent.getStringExtra(ReminderManager.EXTRA_TITLE) ?: "Reminder",
            type = runCatching { ReminderType.valueOf(intent.getStringExtra(ReminderManager.EXTRA_TYPE) ?: ReminderType.CUSTOM.name) }
                .getOrDefault(ReminderType.CUSTOM)
        )
        val nm = context.getSystemService(NotificationManager::class.java)

        when (intent.action) {
            ACTION_DONE -> {
                RoutineProgressStore.markDone(context, reminderId)
                nm?.cancel(reminderId)
            }
            ACTION_SNOOZE -> {
                manager.scheduleSnooze(reminder, minutes = 10)
                nm?.cancel(reminderId)
            }
        }
    }

    companion object {
        const val ACTION_DONE = "com.dailyroutine.app.ACTION_DONE"
        const val ACTION_SNOOZE = "com.dailyroutine.app.ACTION_SNOOZE"
    }
}
