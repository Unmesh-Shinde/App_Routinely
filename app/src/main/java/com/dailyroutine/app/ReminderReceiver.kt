package com.dailyroutine.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(ReminderManager.EXTRA_ID, -1)
        val title = intent.getStringExtra(ReminderManager.EXTRA_TITLE) ?: "Reminder"
        val typeName = intent.getStringExtra(ReminderManager.EXTRA_TYPE) ?: ReminderType.CUSTOM.name
        val triggerKind = intent.getStringExtra(ReminderManager.EXTRA_TRIGGER_KIND)
            ?: ReminderManager.TRIGGER_KIND_ORIGINAL
        val type = runCatching { ReminderType.valueOf(typeName) }.getOrDefault(ReminderType.CUSTOM)

        val manager = ReminderManager(context)
        val saved = if (id >= 0) manager.getReminderById(id) else null
        val reminder = saved ?: Reminder(
            id = id,
            title = title,
            type = type,
            isEnabled = true
        )

        showNotification(context, reminder)
        if (triggerKind == ReminderManager.TRIGGER_KIND_ORIGINAL && saved != null && saved.isEnabled) {
            manager.scheduleReminder(saved)
        }
    }

    private fun showNotification(context: Context, reminder: Reminder) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(nm)

        val openIntent = PendingIntent.getActivity(
            context,
            reminder.id,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val doneIntent = PendingIntent.getBroadcast(
            context,
            reminder.id + 10_000,
            Intent(context, ReminderActionReceiver::class.java).apply {
                action = ReminderActionReceiver.ACTION_DONE
                putExtra(ReminderManager.EXTRA_ID, reminder.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = PendingIntent.getBroadcast(
            context,
            reminder.id + 20_000,
            Intent(context, ReminderActionReceiver::class.java).apply {
                action = ReminderActionReceiver.ACTION_SNOOZE
                putExtra(ReminderManager.EXTRA_ID, reminder.id)
                putExtra(ReminderManager.EXTRA_TITLE, reminder.title)
                putExtra(ReminderManager.EXTRA_TYPE, reminder.type.name)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val message = when (reminder.type) {
            ReminderType.MEAL -> {
                val dish = reminder.dishType.takeIf { it.isNotBlank() } ?: "your scheduled meal"
                "Time for your meal: $dish. Mark as eaten?"
            }
            ReminderType.EXERCISE -> "Workout Time: ${reminder.title}. Ready to crush it?"
            else -> reminder.type.defaultMessage
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${reminder.type.emoji} ${reminder.title}")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setVibrate(longArrayOf(0, 200, 100, 200))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.checkbox_on_background, "Done", doneIntent)
            .addAction(android.R.drawable.ic_menu_recent_history, "Snooze 10m", snoozeIntent)
            .build()

        nm.notify(reminder.id, notification)
    }

    private fun ensureChannel(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttr = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()

            NotificationChannel(
                CHANNEL_ID,
                "Daily Routine Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for your daily routine reminders"
                setSound(sound, audioAttr)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 100, 200)
            }.also { nm.createNotificationChannel(it) }
        }
    }

    companion object {
        const val CHANNEL_ID = "daily_routine_v1"
    }
}
