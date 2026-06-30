package com.dailyroutine.app

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RoutineProgressStore {

    private const val PREFS = "routine_progress_pref"
    private const val PREFIX = "done_"

    fun setDoneStatus(context: Context, id: Int, isDone: Boolean) {
        setDoneStatus(context, todayStamp(), id, isDone)
    }

    fun setDoneStatus(context: Context, date: String, id: Int, isDone: Boolean) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = keyForDate(date)
        val current = prefs.getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (isDone) current.add(id.toString()) else current.remove(id.toString())
        prefs.edit().putStringSet(key, current).apply()
    }

    fun markDone(context: Context, reminderId: Int) {
        setDoneStatus(context, reminderId, true)
    }

    fun getDoneCount(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getStringSet(todayKey(), emptySet())?.size ?: 0
    }

    fun getDoneIds(context: Context): Set<String> {
        return getDoneIds(context, todayStamp())
    }

    fun getDoneIds(context: Context, date: String): Set<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getStringSet(keyForDate(date), emptySet()) ?: emptySet()
    }

    fun clearToday(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(todayKey())
            .apply()
    }

    private fun todayKey(): String {
        return keyForDate(todayStamp())
    }

    private fun todayStamp(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    private fun keyForDate(date: String): String {
        return PREFIX + date
    }
}
