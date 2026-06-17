package com.dailyroutine.app

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getIntExtra(ReminderManager.EXTRA_ID, -1)
        if (reminderId == -1) return

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
                android.widget.Toast.makeText(context, "Habit Completed! ✅", android.widget.Toast.LENGTH_SHORT).show()
            }
            ACTION_SNOOZE -> {
                manager.scheduleSnooze(reminder, minutes = 10)
                nm?.cancel(reminderId)
                android.widget.Toast.makeText(context, "Snoozed for 10 min ⏰", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        const val ACTION_DONE = "com.dailyroutine.app.ACTION_DONE"
        const val ACTION_SNOOZE = "com.dailyroutine.app.ACTION_SNOOZE"
    }
}
