package com.dailyroutine.app

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RoutineProgressStore {

    private const val PREFS = "routine_progress_pref"
    private const val PREFIX = "done_"

    fun markDone(context: Context, reminderId: Int) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = todayKey()
        val current = prefs.getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(reminderId.toString())
        prefs.edit().putStringSet(key, current).apply()
    }

    fun getDoneCount(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getStringSet(todayKey(), emptySet())?.size ?: 0
    }

    fun clearToday(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(todayKey())
            .apply()
    }

    private fun todayKey(): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return PREFIX + stamp
    }
}
