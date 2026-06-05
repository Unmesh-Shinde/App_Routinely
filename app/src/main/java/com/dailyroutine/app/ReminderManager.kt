package com.dailyroutine.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Calendar

class ReminderManager(context: Context) {

    private val context: Context = context.applicationContext
    private val prefs: SharedPreferences =
        this.context.getSharedPreferences("reminders_pref", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val alarmManager =
        this.context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun getAllReminders(): MutableList<Reminder> {
        val json = prefs.getString(KEY_LIST, null) ?: return mutableListOf()
        return try {
            val type = object : TypeToken<MutableList<Reminder>>() {}.type
            gson.fromJson<MutableList<Reminder>>(json, type) ?: mutableListOf<Reminder>()
        } catch (_: Exception) {
            mutableListOf<Reminder>()
        }.sortedWith(
            compareBy<Reminder> { !it.isEnabled }
                .thenBy { if (it.isIntervalBased) 1 else 0 }
                .thenBy { it.type.ordinal }
                .thenBy { if (it.isIntervalBased) it.intervalMinutes else it.hour * 60 + it.minute }
                .thenBy { it.title.lowercase() }
        ).toMutableList()
    }

    fun getReminderById(id: Int): Reminder? = getAllReminders().find { it.id == id }

    fun saveReminder(reminder: Reminder) {
        val list = getAllReminders()
        val idx = list.indexOfFirst { it.id == reminder.id }
        if (idx >= 0) list[idx] = reminder else list.add(reminder)
        persistList(list)
        cancelReminder(reminder)
        if (reminder.isEnabled) scheduleReminder(reminder)
    }

    fun deleteReminder(reminder: Reminder) {
        cancelReminder(reminder)
        val list = getAllReminders()
        list.removeAll { it.id == reminder.id }
        persistList(list)
    }

    fun toggleReminder(reminder: Reminder) {
        saveReminder(reminder.copy(isEnabled = !reminder.isEnabled))
    }

    fun getNextTriggerTime(reminder: Reminder): Long? =
        if (reminder.isIntervalBased) {
            System.currentTimeMillis() + reminder.intervalMinutes * 60_000L
        } else {
            nextFixedTriggerMs(reminder)
        }

    private fun persistList(list: List<Reminder>) {
        prefs.edit().putString(KEY_LIST, gson.toJson(list)).apply()
    }

    fun scheduleAllEnabled() {
        getAllReminders().filter { it.isEnabled }.forEach { scheduleReminder(it) }
    }

    fun scheduleReminder(reminder: Reminder) {
        if (!reminder.isEnabled) return
        if (reminder.isIntervalBased) scheduleInterval(reminder)
        else scheduleFixedTime(reminder)
    }

    fun scheduleSnooze(reminder: Reminder, minutes: Int = 10) {
        val triggerMs = System.currentTimeMillis() + minutes * 60_000L
        val pi = buildPendingIntent(
            reminder = reminder,
            requestCode = SNOOZE_REQUEST_CODE + reminder.id,
            triggerKind = TRIGGER_KIND_SNOOZE
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
            else
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerMs, pi)
        } catch (_: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMs, pi)
        }
    }

    private fun scheduleFixedTime(reminder: Reminder) {
        if (reminder.repeatDays.isEmpty()) return

        val triggerMs = nextFixedTriggerMs(reminder) ?: return
        val pi = buildPendingIntent(reminder, FIXED_REQUEST_CODE + reminder.id, TRIGGER_KIND_ORIGINAL)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
            else
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerMs, pi)
        } catch (_: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMs, pi)
        }
    }

    private fun scheduleInterval(reminder: Reminder) {
        val triggerMs = System.currentTimeMillis() + reminder.intervalMinutes * 60_000L
        val pi = buildPendingIntent(reminder, INTERVAL_REQUEST_CODE + reminder.id, TRIGGER_KIND_ORIGINAL)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
            else
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerMs, pi)
        } catch (_: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMs, pi)
        }
    }

    fun cancelReminder(reminder: Reminder) {
        listOf(
            buildPendingIntent(reminder, FIXED_REQUEST_CODE + reminder.id, TRIGGER_KIND_ORIGINAL),
            buildPendingIntent(reminder, INTERVAL_REQUEST_CODE + reminder.id, TRIGGER_KIND_ORIGINAL),
            buildPendingIntent(reminder, SNOOZE_REQUEST_CODE + reminder.id, TRIGGER_KIND_SNOOZE)
        ).forEach { alarmManager.cancel(it) }
    }

    private fun nextFixedTriggerMs(reminder: Reminder): Long? {
        val now = Calendar.getInstance()
        for (daysAhead in 0..7) {
            val cal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, daysAhead)
                set(Calendar.HOUR_OF_DAY, reminder.hour)
                set(Calendar.MINUTE, reminder.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (daysAhead == 0 && cal.before(now)) continue
            val calDay = cal.get(Calendar.DAY_OF_WEEK)
            val ourDay = if (calDay == Calendar.SUNDAY) 7 else calDay - 1
            if (ourDay in reminder.repeatDays) return cal.timeInMillis
        }
        return null
    }

    private fun buildPendingIntent(
        reminder: Reminder,
        requestCode: Int,
        triggerKind: String
    ): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_ID, reminder.id)
            putExtra(EXTRA_TITLE, reminder.title)
            putExtra(EXTRA_TYPE, reminder.type.name)
            putExtra(EXTRA_IS_INTERVAL, reminder.isIntervalBased)
            putExtra(EXTRA_INTERVAL_MIN, reminder.intervalMinutes)
            putExtra(EXTRA_TRIGGER_KIND, triggerKind)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val KEY_LIST = "reminder_list"
        private const val FIXED_REQUEST_CODE = 1000
        private const val INTERVAL_REQUEST_CODE = 5000
        private const val SNOOZE_REQUEST_CODE = 9000

        const val EXTRA_ID = "reminder_id"
        const val EXTRA_TITLE = "reminder_title"
        const val EXTRA_TYPE = "reminder_type"
        const val EXTRA_IS_INTERVAL = "is_interval"
        const val EXTRA_INTERVAL_MIN = "interval_min"
        const val EXTRA_TRIGGER_KIND = "trigger_kind"

        const val TRIGGER_KIND_ORIGINAL = "original"
        const val TRIGGER_KIND_SNOOZE = "snooze"
    }
}
