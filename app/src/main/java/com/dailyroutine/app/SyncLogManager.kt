package com.dailyroutine.app

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

data class SyncEntry(
    val timestamp: Long,
    val source: String,
    val message: String,
    val isSuccess: Boolean
) {
    fun formatTime(): String {
        return SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(timestamp))
    }
}

object SyncLogManager {
    private const val PREFS_NAME = "sync_logs_pref"
    private const val KEY_LOGS = "daily_sync_logs"
    private const val KEY_LAST_RESET_DATE = "last_log_reset_date"

    private fun getPrefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun addLog(context: Context, source: String, message: String, isSuccess: Boolean) {
        checkAndResetIfNeeded(context)

        val logs = getLogs(context).toMutableList()
        logs.add(0, SyncEntry(System.currentTimeMillis(), source, message, isSuccess))
        
        // Keep only reasonable amount of logs for the day
        val trimmed = logs.take(50)
        
        val json = Gson().toJson(trimmed)
        getPrefs(context).edit().putString(KEY_LOGS, json).apply()
    }

    fun getLogs(context: Context): List<SyncEntry> {
        val json = getPrefs(context).getString(KEY_LOGS, null) ?: return emptyList()
        val type = object : TypeToken<List<SyncEntry>>() {}.type
        return try {
            Gson().fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun checkAndResetIfNeeded(context: Context) {
        val now = Calendar.getInstance()
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now.time)
        val lastReset = getPrefs(context).getString(KEY_LAST_RESET_DATE, "")
        
        // Logic: If new day AND it's at or after 6:00 AM, clear old logs.
        if (lastReset != todayStr && now.get(Calendar.HOUR_OF_DAY) >= 6) {
            getPrefs(context).edit()
                .putString(KEY_LOGS, "[]")
                .putString(KEY_LAST_RESET_DATE, todayStr)
                .apply()
        }
    }
}
