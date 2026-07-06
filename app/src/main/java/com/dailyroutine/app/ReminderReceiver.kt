package com.dailyroutine.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        val wakeLock = powerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "DailyRoutine:ReminderWakeLock")
        wakeLock.acquire(5000) // Acquire for 5 seconds to ensure notification shows
        try {
            val id = intent.getIntExtra(ReminderManager.EXTRA_ID, -1)
            val title = intent.getStringExtra(ReminderManager.EXTRA_TITLE) ?: "Reminder"
            val typeName = intent.getStringExtra(ReminderManager.EXTRA_TYPE) ?: ReminderType.CUSTOM.name
            val triggerKind = intent.getStringExtra(ReminderManager.EXTRA_TRIGGER_KIND)
                ?: ReminderManager.TRIGGER_KIND_ORIGINAL
            val type = runCatching { ReminderType.valueOf(typeName) }.getOrDefault(ReminderType.CUSTOM)

            val manager = ReminderManager(context)
            val saved = if (id >= 0) manager.getReminderById(id) else null
            if (id >= 0 && saved?.isEnabled != true) {
                return
            }

            val reminder = saved ?: Reminder(
                id = id,
                title = title,
                type = type,
                isEnabled = true
            )

            showNotification(context, reminder)
            if (triggerKind == ReminderManager.TRIGGER_KIND_ORIGINAL) {
                manager.scheduleReminder(reminder)
            }
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
        }
    }

    private fun showNotification(context: Context, reminder: Reminder) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val soundUri = resolveNotificationSound(context, reminder)
        val channelId = channelIdForSound(soundUri)
        ensureChannel(nm, channelId, soundUri)

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
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
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
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val message = when (reminder.type) {
            ReminderType.MEAL -> {
                val dish = reminder.dishType.takeIf { it.isNotBlank() } ?: "your scheduled meal"
                "Time for your meal: $dish. Mark as eaten?"
            }
            ReminderType.EXERCISE -> "Workout Time: ${reminder.title}. Ready to crush it?"
            else -> reminder.type.defaultMessage
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(reminder.title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 200, 100, 200))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openIntent)
            .addAction(R.drawable.ic_check, "Done", doneIntent)
            .addAction(R.drawable.ic_snooze, "Snooze 10m", snoozeIntent)
            .build()

        nm.notify(reminder.id, notification)
    }

    private fun resolveNotificationSound(context: Context, reminder: Reminder): Uri {
        val selectedUri = reminder.soundUri
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }

        if (selectedUri != null && ReminderToneHelper.isToneDurationAllowed(context, selectedUri)) {
            return selectedUri
        }

        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }

    private fun channelIdForSound(soundUri: Uri): String {
        return "${CHANNEL_ID}_${Integer.toHexString(soundUri.toString().hashCode())}"
    }

    private fun ensureChannel(nm: NotificationManager, channelId: String, soundUri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (nm.getNotificationChannel(channelId) != null) return

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            NotificationChannel(
                channelId,
                "Routinely Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for your Routinely reminders"
                setSound(soundUri, audioAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 100, 200)
            }.also { nm.createNotificationChannel(it) }
        }
    }

    companion object {
        const val CHANNEL_ID = "daily_routine_v1"
    }
}
